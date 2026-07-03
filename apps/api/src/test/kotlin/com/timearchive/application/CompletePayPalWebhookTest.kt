package com.timearchive.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.timearchive.adapter.outbound.payment.PayPalWebhookVerificationCommand
import com.timearchive.adapter.outbound.payment.PayPalWebhookVerifierClient
import com.timearchive.configuration.PayPalPaymentProperties
import com.timearchive.domain.model.CheckoutAttempt
import com.timearchive.domain.model.CheckoutAttemptStatus
import com.timearchive.domain.model.CheckoutSession
import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.CheckoutAttemptRepository
import com.timearchive.domain.port.PurchaseReservationRepository
import com.timearchive.domain.port.TransactionPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CompletePayPalWebhookTest {
    private val now: Instant = Instant.parse("2026-07-03T00:00:00Z")
    private val reservationId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000101")

    @Test
    fun `finalizes verified completed capture`() {
        val completePrimaryPurchase = mockk<CompletePrimaryPurchase>()
        every { completePrimaryPurchase.complete(any()) } returns CompletePrimaryPurchase.Result(
            purchaseId = UUID.fromString("00000000-0000-0000-0000-000000000201"),
            ownershipRecordId = UUID.fromString("00000000-0000-0000-0000-000000000301"),
            alreadyProcessed = false,
        )
        val useCase = useCase(completePrimaryPurchase = completePrimaryPurchase)

        val result = useCase.complete(command(completedCaptureBody()))

        assertThat(result.status).isEqualTo("COMPLETED")
        assertThat(result.eventType).isEqualTo("PAYMENT.CAPTURE.COMPLETED")
        assertThat(result.alreadyProcessed).isFalse()
        verify {
            completePrimaryPurchase.complete(
                withArg {
                    assertThat(it.provider).isEqualTo("paypal")
                    assertThat(it.providerEventId).isEqualTo("WH-1")
                    assertThat(it.eventType).isEqualTo("PAYMENT.CAPTURE.COMPLETED")
                    assertThat(it.reservationId).isEqualTo(reservationId)
                    assertThat(it.paymentReference).isEqualTo("CAPTURE-1")
                    assertThat(it.paymentCompletedAt).isEqualTo(Instant.parse("2026-07-03T00:01:00Z"))
                    assertThat(it.payloadHash).startsWith("sha256:")
                },
            )
        }
    }

    @Test
    fun `rejects unverified signature`() {
        val useCase = useCase(verifier = FakePayPalWebhookVerifierClient(verified = false))

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.complete(command(completedCaptureBody())) }
            .withMessage("paypal webhook signature verification failed")
    }

    @Test
    fun `ignores verified unsupported event without completing purchase`() {
        val completePrimaryPurchase = mockk<CompletePrimaryPurchase>(relaxed = true)
        val useCase = useCase(completePrimaryPurchase = completePrimaryPurchase)

        val result = useCase.complete(command("""{"id":"WH-ignored","event_type":"PAYMENT.CAPTURE.DENIED"}"""))

        assertThat(result.status).isEqualTo("IGNORED")
        assertThat(result.eventType).isEqualTo("PAYMENT.CAPTURE.DENIED")
        verify(exactly = 0) { completePrimaryPurchase.complete(any()) }
    }

    @Test
    fun `rejects capture reference mismatch`() {
        val attempt = checkoutAttempt().copy(captureReference = "other-capture")
        val useCase = useCase(checkoutAttemptRepository = FakeCheckoutAttemptRepository(attempt))

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.complete(command(completedCaptureBody())) }
            .withMessage("paypal capture reference mismatch")
    }

    private fun useCase(
        reservation: PurchaseReservation = reservation(),
        checkoutAttemptRepository: CheckoutAttemptRepository = FakeCheckoutAttemptRepository(checkoutAttempt()),
        purchaseReservationRepository: PurchaseReservationRepository = FakePurchaseReservationRepository(reservation),
        verifier: PayPalWebhookVerifierClient = FakePayPalWebhookVerifierClient(),
        completePrimaryPurchase: CompletePrimaryPurchase = mockk {
            every { complete(any()) } returns CompletePrimaryPurchase.Result(
                purchaseId = UUID.randomUUID(),
                ownershipRecordId = UUID.randomUUID(),
                alreadyProcessed = false,
            )
        },
    ): CompletePayPalWebhook =
        CompletePayPalWebhook(
            objectMapper = jacksonObjectMapper().findAndRegisterModules(),
            transactionPort = ImmediateTransactionPort,
            checkoutAttemptRepository = checkoutAttemptRepository,
            purchaseReservationRepository = purchaseReservationRepository,
            paypalWebhookVerifierClient = verifier,
            completePrimaryPurchase = completePrimaryPurchase,
            properties = PayPalPaymentProperties(
                enabled = true,
                apiBaseUrl = "https://api-m.sandbox.paypal.com",
                clientId = "client-id",
                clientSecret = "client-secret",
                returnUrl = "https://staging.time-archive.com/payments/paypal/return",
                cancelUrl = "https://staging.time-archive.com/payments/paypal/cancel",
                webhookId = "WEBHOOK-1",
            ),
        )

    private fun command(rawBody: String): CompletePayPalWebhook.Command =
        CompletePayPalWebhook.Command(
            rawBody = rawBody,
            headers = CompletePayPalWebhook.Headers(
                transmissionId = "transmission-1",
                transmissionTime = "2026-07-03T00:02:00Z",
                certUrl = "https://api-m.sandbox.paypal.com/certs/cert.pem",
                authAlgo = "SHA256withRSA",
                transmissionSig = "signature",
            ),
            requestId = "request-1",
        )

    private fun completedCaptureBody(): String =
        """
        {
          "id": "WH-1",
          "event_type": "PAYMENT.CAPTURE.COMPLETED",
          "resource": {
            "id": "CAPTURE-1",
            "status": "COMPLETED",
            "custom_id": "$reservationId",
            "update_time": "2026-07-03T00:01:00Z",
            "amount": {
              "currency_code": "USD",
              "value": "2.00"
            },
            "supplementary_data": {
              "related_ids": {
                "order_id": "ORDER-1"
              }
            }
          }
        }
        """.trimIndent()

    private fun reservation(): PurchaseReservation =
        PurchaseReservation.held(
            id = reservationId,
            buyerId = UUID.randomUUID(),
            range = TimeRange(startSecond = 10, endSecond = 12),
            now = now,
            expiresAt = now.plusSeconds(600),
        ).copy(status = PurchaseReservationStatus.CHECKOUT_CREATED)

    private fun checkoutAttempt(): CheckoutAttempt =
        CheckoutAttempt(
            id = UUID.randomUUID(),
            reservationId = reservationId,
            buyerId = UUID.randomUUID(),
            provider = "paypal",
            providerRequestId = "provider-request-1",
            providerReference = "ORDER-1",
            checkoutUrl = "https://www.sandbox.paypal.com/checkoutnow?token=ORDER-1",
            captureRequestId = "capture-request-1",
            captureReference = "CAPTURE-1",
            capturedAt = Instant.parse("2026-07-03T00:01:00Z"),
            status = CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK,
            createdAt = now,
            updatedAt = now,
        )

    private object ImmediateTransactionPort : TransactionPort {
        override fun <T> execute(block: () -> T): T = block()
    }

    private class FakePayPalWebhookVerifierClient(
        private val verified: Boolean = true,
    ) : PayPalWebhookVerifierClient {
        val commands = mutableListOf<PayPalWebhookVerificationCommand>()

        override fun verify(command: PayPalWebhookVerificationCommand): Boolean {
            commands.add(command)
            return verified
        }
    }

    private class FakeCheckoutAttemptRepository(
        private val attempt: CheckoutAttempt?,
    ) : CheckoutAttemptRepository {
        override fun save(attempt: CheckoutAttempt): CheckoutAttempt = attempt

        override fun findByReservationIdForUpdate(reservationId: UUID): CheckoutAttempt? =
            attempt?.takeIf { it.reservationId == reservationId }

        override fun findByProviderReferenceForUpdate(
            provider: String,
            providerReference: String,
        ): CheckoutAttempt? = null

        override fun markProviderCreated(
            id: UUID,
            providerReference: String,
            checkoutUrl: String,
            now: Instant,
        ): Int = 0

        override fun markProviderFailed(id: UUID, now: Instant): Int = 0

        override fun markCaptureCompleted(
            id: UUID,
            captureRequestId: String,
            captureReference: String,
            capturedAt: Instant,
        ): Int = 0

        override fun markCaptureFailed(
            id: UUID,
            captureRequestId: String,
            now: Instant,
        ): Int = 0
    }

    private class FakePurchaseReservationRepository(
        private val reservation: PurchaseReservation?,
    ) : PurchaseReservationRepository {
        override fun save(reservation: PurchaseReservation): PurchaseReservation = reservation

        override fun findByIdForUpdate(id: UUID): PurchaseReservation? =
            reservation?.takeIf { it.id == id }

        override fun findActiveOverlapping(range: TimeRange): List<PurchaseReservation> = emptyList()

        override fun expireOverdue(now: Instant): Int = 0

        override fun markCheckoutCreated(id: UUID, now: Instant): Int = 0

        override fun markCompleted(id: UUID, now: Instant): Int = 1
    }
}
