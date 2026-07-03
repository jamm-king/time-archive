package com.timearchive.application

import com.timearchive.adapter.outbound.payment.PayPalCaptureOrderCommand
import com.timearchive.adapter.outbound.payment.PayPalCaptureResult
import com.timearchive.adapter.outbound.payment.PayPalCreateOrderCommand
import com.timearchive.adapter.outbound.payment.PayPalOrderClient
import com.timearchive.adapter.outbound.payment.PayPalOrderResult
import com.timearchive.domain.model.CheckoutAttempt
import com.timearchive.domain.model.CheckoutAttemptStatus
import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.CheckoutAttemptRepository
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.PurchaseReservationRepository
import com.timearchive.domain.port.TransactionPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CapturePayPalOrderTest {
    private val now: Instant = Instant.parse("2026-07-03T00:00:00Z")

    @Test
    fun `captures paypal order and records pending webhook status`() {
        val reservation = checkoutCreatedReservation()
        val attempt = providerCreatedAttempt(reservation)
        val checkoutAttemptRepository = FakeCheckoutAttemptRepository(attempt)
        val paypalOrderClient = FakePayPalOrderClient(
            captureResult = PayPalCaptureResult(
                orderId = "paypal-order-1",
                captureId = "paypal-capture-1",
                status = "COMPLETED",
            ),
        )
        val useCase = useCase(
            checkoutAttemptRepository = checkoutAttemptRepository,
            purchaseReservationRepository = FakePurchaseReservationRepository(reservation),
            paypalOrderClient = paypalOrderClient,
        )

        val result = useCase.capture(
            CapturePayPalOrder.Command(
                currentUserId = reservation.buyerId,
                orderId = "paypal-order-1",
            ),
        )

        assertThat(result.orderId).isEqualTo("paypal-order-1")
        assertThat(result.captureReference).isEqualTo("paypal-capture-1")
        assertThat(result.status).isEqualTo("CAPTURED_PENDING_WEBHOOK")
        assertThat(result.alreadyCaptured).isFalse()
        assertThat(paypalOrderClient.captureCommands.single().providerRequestId)
            .isEqualTo("capture-${attempt.id}")
        assertThat(checkoutAttemptRepository.attempt?.status)
            .isEqualTo(CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK)
        assertThat(checkoutAttemptRepository.attempt?.captureReference)
            .isEqualTo("paypal-capture-1")
    }

    @Test
    fun `returns existing captured result without calling paypal again`() {
        val reservation = checkoutCreatedReservation()
        val attempt = providerCreatedAttempt(reservation).copy(
            status = CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK,
            captureRequestId = "capture-request-1",
            captureReference = "paypal-capture-1",
            capturedAt = now,
        )
        val paypalOrderClient = FakePayPalOrderClient()
        val useCase = useCase(
            checkoutAttemptRepository = FakeCheckoutAttemptRepository(attempt),
            purchaseReservationRepository = FakePurchaseReservationRepository(reservation),
            paypalOrderClient = paypalOrderClient,
        )

        val result = useCase.capture(
            CapturePayPalOrder.Command(
                currentUserId = reservation.buyerId,
                orderId = "paypal-order-1",
            ),
        )

        assertThat(result.captureReference).isEqualTo("paypal-capture-1")
        assertThat(result.alreadyCaptured).isTrue()
        assertThat(paypalOrderClient.captureCommands).isEmpty()
    }

    @Test
    fun `rejects capture for another user`() {
        val reservation = checkoutCreatedReservation()
        val useCase = useCase(
            checkoutAttemptRepository = FakeCheckoutAttemptRepository(providerCreatedAttempt(reservation)),
            purchaseReservationRepository = FakePurchaseReservationRepository(reservation),
        )

        assertThatThrownBy {
            useCase.capture(
                CapturePayPalOrder.Command(
                    currentUserId = UUID.randomUUID(),
                    orderId = "paypal-order-1",
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("checkout attempt is not owned by current user")
    }

    @Test
    fun `rejects capture after reservation expiration`() {
        val reservation = checkoutCreatedReservation().copy(expiresAt = now)
        val useCase = useCase(
            checkoutAttemptRepository = FakeCheckoutAttemptRepository(providerCreatedAttempt(reservation)),
            purchaseReservationRepository = FakePurchaseReservationRepository(reservation),
        )

        assertThatThrownBy {
            useCase.capture(
                CapturePayPalOrder.Command(
                    currentUserId = reservation.buyerId,
                    orderId = "paypal-order-1",
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("reservation is expired")
    }

    @Test
    fun `marks capture failed when paypal capture fails`() {
        val reservation = checkoutCreatedReservation()
        val attempt = providerCreatedAttempt(reservation)
        val checkoutAttemptRepository = FakeCheckoutAttemptRepository(attempt)
        val useCase = useCase(
            checkoutAttemptRepository = checkoutAttemptRepository,
            purchaseReservationRepository = FakePurchaseReservationRepository(reservation),
            paypalOrderClient = FakePayPalOrderClient(captureFailure = IllegalStateException("paypal unavailable")),
        )

        assertThatThrownBy {
            useCase.capture(
                CapturePayPalOrder.Command(
                    currentUserId = reservation.buyerId,
                    orderId = "paypal-order-1",
                ),
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("paypal unavailable")

        assertThat(checkoutAttemptRepository.attempt?.status).isEqualTo(CheckoutAttemptStatus.CAPTURE_FAILED)
        assertThat(checkoutAttemptRepository.attempt?.captureRequestId).isEqualTo("capture-${attempt.id}")
    }

    private fun useCase(
        checkoutAttemptRepository: CheckoutAttemptRepository = FakeCheckoutAttemptRepository(),
        purchaseReservationRepository: PurchaseReservationRepository =
            FakePurchaseReservationRepository(checkoutCreatedReservation()),
        paypalOrderClient: PayPalOrderClient = FakePayPalOrderClient(),
    ): CapturePayPalOrder =
        CapturePayPalOrder(
            transactionPort = ImmediateTransactionPort,
            checkoutAttemptRepository = checkoutAttemptRepository,
            purchaseReservationRepository = purchaseReservationRepository,
            paypalOrderClient = paypalOrderClient,
            clockPort = ClockPort { now },
        )

    private fun checkoutCreatedReservation(): PurchaseReservation =
        PurchaseReservation.held(
            id = UUID.randomUUID(),
            buyerId = UUID.randomUUID(),
            range = TimeRange(startSecond = 10, endSecond = 12),
            now = now.minusSeconds(60),
            expiresAt = now.plusSeconds(600),
        ).copy(status = PurchaseReservationStatus.CHECKOUT_CREATED)

    private fun providerCreatedAttempt(reservation: PurchaseReservation): CheckoutAttempt =
        CheckoutAttempt.pending(
            id = UUID.randomUUID(),
            reservation = reservation,
            provider = "paypal",
            providerRequestId = UUID.randomUUID().toString(),
            now = now.minusSeconds(30),
        ).copy(
            providerReference = "paypal-order-1",
            checkoutUrl = "https://www.sandbox.paypal.com/checkoutnow?token=paypal-order-1",
            status = CheckoutAttemptStatus.PROVIDER_CREATED,
        )

    private object ImmediateTransactionPort : TransactionPort {
        override fun <T> execute(block: () -> T): T = block()
    }

    private class FakeCheckoutAttemptRepository(
        var attempt: CheckoutAttempt? = null,
    ) : CheckoutAttemptRepository {
        override fun save(attempt: CheckoutAttempt): CheckoutAttempt {
            this.attempt = attempt
            return attempt
        }

        override fun findByReservationIdForUpdate(reservationId: UUID): CheckoutAttempt? =
            attempt?.takeIf { it.reservationId == reservationId }

        override fun findByProviderReferenceForUpdate(
            provider: String,
            providerReference: String,
        ): CheckoutAttempt? =
            attempt?.takeIf { it.provider == provider && it.providerReference == providerReference }

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
        ): Int {
            val current = attempt ?: return 0
            if (current.id != id || current.status !in setOf(
                    CheckoutAttemptStatus.PROVIDER_CREATED,
                    CheckoutAttemptStatus.CAPTURE_FAILED,
                )
            ) {
                return 0
            }
            attempt = current.copy(
                status = CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK,
                captureRequestId = captureRequestId,
                captureReference = captureReference,
                capturedAt = capturedAt,
                updatedAt = capturedAt,
            )
            return 1
        }

        override fun markCaptureFailed(
            id: UUID,
            captureRequestId: String,
            now: Instant,
        ): Int {
            val current = attempt ?: return 0
            if (current.id != id || current.status !in setOf(
                    CheckoutAttemptStatus.PROVIDER_CREATED,
                    CheckoutAttemptStatus.CAPTURE_FAILED,
                )
            ) {
                return 0
            }
            attempt = current.copy(
                status = CheckoutAttemptStatus.CAPTURE_FAILED,
                captureRequestId = captureRequestId,
                updatedAt = now,
            )
            return 1
        }
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

        override fun markCompleted(id: UUID, now: Instant): Int = 0
    }

    private class FakePayPalOrderClient(
        private val captureResult: PayPalCaptureResult = PayPalCaptureResult(
            orderId = "paypal-order-1",
            captureId = "paypal-capture-1",
            status = "COMPLETED",
        ),
        private val captureFailure: RuntimeException? = null,
    ) : PayPalOrderClient {
        val captureCommands = mutableListOf<PayPalCaptureOrderCommand>()

        override fun createOrder(command: PayPalCreateOrderCommand): PayPalOrderResult =
            error("not used")

        override fun captureOrder(command: PayPalCaptureOrderCommand): PayPalCaptureResult {
            captureCommands.add(command)
            captureFailure?.let { throw it }
            return captureResult
        }
    }
}
