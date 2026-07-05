package com.timearchive.application

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.CheckoutAttempt
import com.timearchive.domain.model.CheckoutAttemptStatus
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.Purchase
import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.CheckoutAttemptRepository
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.PurchaseRepository
import com.timearchive.domain.port.PurchaseReservationRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GetPayPalOrderConfirmationStatusTest {
    private val now: Instant = Instant.parse("2026-07-05T00:00:00Z")

    @Test
    fun `returns pending webhook status after capture`() {
        val reservation = checkoutCreatedReservation()
        val attempt = paypalAttempt(reservation, CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK)
        val useCase = useCase(
            attempt = attempt,
            reservation = reservation,
        )

        val result = useCase.get(command(reservation.buyerId))

        assertThat(result.status)
            .isEqualTo(GetPayPalOrderConfirmationStatus.ConfirmationStatus.CAPTURE_PENDING_WEBHOOK)
        assertThat(result.terminal).isFalse()
        assertThat(result.purchaseId).isNull()
        assertThat(result.ownershipRecordId).isNull()
    }

    @Test
    fun `returns ownership granted when purchase and active ownership exist`() {
        val reservation = checkoutCreatedReservation().copy(status = PurchaseReservationStatus.COMPLETED)
        val attempt = paypalAttempt(reservation, CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK)
        val purchase = Purchase.ownershipGranted(
            id = UUID.randomUUID(),
            reservation = reservation,
            paymentProvider = "paypal",
            paymentReference = "paypal-capture-1",
            now = now,
        )
        val ownership = OwnershipRecord.active(
            id = UUID.randomUUID(),
            range = reservation.range,
            ownerId = reservation.buyerId,
            validFrom = now,
            acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
            sourcePurchaseId = purchase.id,
        )
        val useCase = useCase(
            attempt = attempt,
            reservation = reservation,
            purchase = purchase,
            ownership = ownership,
        )

        val result = useCase.get(command(reservation.buyerId))

        assertThat(result.status)
            .isEqualTo(GetPayPalOrderConfirmationStatus.ConfirmationStatus.OWNERSHIP_GRANTED)
        assertThat(result.terminal).isTrue()
        assertThat(result.purchaseId).isEqualTo(purchase.id)
        assertThat(result.ownershipRecordId).isEqualTo(ownership.id)
    }

    @Test
    fun `returns expired when reservation expired before webhook finalization`() {
        val reservation = checkoutCreatedReservation().copy(expiresAt = now.minusSeconds(1))
        val attempt = paypalAttempt(reservation, CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK)
        val useCase = useCase(
            attempt = attempt,
            reservation = reservation,
        )

        val result = useCase.get(command(reservation.buyerId))

        assertThat(result.status)
            .isEqualTo(GetPayPalOrderConfirmationStatus.ConfirmationStatus.EXPIRED)
        assertThat(result.terminal).isTrue()
    }

    @Test
    fun `rejects checkout status for another user`() {
        val reservation = checkoutCreatedReservation()
        val attempt = paypalAttempt(reservation, CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK)
        val useCase = useCase(
            attempt = attempt,
            reservation = reservation,
        )

        assertThatThrownBy {
            useCase.get(command(UUID.randomUUID()))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("checkout attempt is not owned by current user")
    }

    private fun useCase(
        attempt: CheckoutAttempt,
        reservation: PurchaseReservation,
        purchase: Purchase? = null,
        ownership: OwnershipRecord? = null,
    ): GetPayPalOrderConfirmationStatus =
        GetPayPalOrderConfirmationStatus(
            checkoutAttemptRepository = FakeCheckoutAttemptRepository(attempt),
            purchaseReservationRepository = FakePurchaseReservationRepository(reservation),
            purchaseRepository = FakePurchaseRepository(purchase),
            ownershipRepository = FakeOwnershipRepository(ownership),
            clockPort = ClockPort { now },
        )

    private fun command(currentUserId: UUID): GetPayPalOrderConfirmationStatus.Command =
        GetPayPalOrderConfirmationStatus.Command(
            currentUserId = currentUserId,
            orderId = "paypal-order-1",
        )

    private fun checkoutCreatedReservation(): PurchaseReservation =
        PurchaseReservation.held(
            id = UUID.randomUUID(),
            buyerId = UUID.randomUUID(),
            range = TimeRange(startSecond = 10, endSecond = 20),
            now = now.minusSeconds(60),
            expiresAt = now.plusSeconds(600),
        ).copy(status = PurchaseReservationStatus.CHECKOUT_CREATED)

    private fun paypalAttempt(
        reservation: PurchaseReservation,
        status: CheckoutAttemptStatus,
    ): CheckoutAttempt =
        CheckoutAttempt.pending(
            id = UUID.randomUUID(),
            reservation = reservation,
            provider = "paypal",
            providerRequestId = UUID.randomUUID().toString(),
            now = now.minusSeconds(30),
        ).copy(
            providerReference = "paypal-order-1",
            checkoutUrl = "https://www.sandbox.paypal.com/checkoutnow?token=paypal-order-1",
            captureRequestId = "capture-request-1",
            captureReference = "paypal-capture-1",
            capturedAt = if (status == CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK) now else null,
            status = status,
        )

    private class FakeCheckoutAttemptRepository(
        private val attempt: CheckoutAttempt,
    ) : CheckoutAttemptRepository {
        override fun save(attempt: CheckoutAttempt): CheckoutAttempt = attempt

        override fun findByReservationIdForUpdate(reservationId: UUID): CheckoutAttempt? =
            attempt.takeIf { it.reservationId == reservationId }

        override fun findByProviderReference(provider: String, providerReference: String): CheckoutAttempt? =
            attempt.takeIf { it.provider == provider && it.providerReference == providerReference }

        override fun findByProviderReferenceForUpdate(
            provider: String,
            providerReference: String,
        ): CheckoutAttempt? = findByProviderReference(provider, providerReference)

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
        private val reservation: PurchaseReservation,
    ) : PurchaseReservationRepository {
        override fun save(reservation: PurchaseReservation): PurchaseReservation = reservation

        override fun findById(id: UUID): PurchaseReservation? =
            reservation.takeIf { it.id == id }

        override fun findByIdForUpdate(id: UUID): PurchaseReservation? = findById(id)

        override fun findActiveOverlapping(range: TimeRange): List<PurchaseReservation> = emptyList()

        override fun expireOverdue(now: Instant): Int = 0

        override fun markCheckoutCreated(id: UUID, now: Instant): Int = 0

        override fun markCompleted(id: UUID, now: Instant): Int = 0
    }

    private class FakePurchaseRepository(
        private val purchase: Purchase?,
    ) : PurchaseRepository {
        override fun save(purchase: Purchase): Purchase = purchase

        override fun findByReservationId(reservationId: UUID): Purchase? =
            purchase?.takeIf { it.reservationId == reservationId }
    }

    private class FakeOwnershipRepository(
        private val ownership: OwnershipRecord?,
    ) : OwnershipRepository {
        override fun save(record: OwnershipRecord): OwnershipRecord = record

        override fun findById(id: UUID): OwnershipRecord? =
            ownership?.takeIf { it.id == id }

        override fun findActiveByOwnerId(ownerId: UUID): List<OwnershipRecord> =
            ownership?.takeIf { it.ownerId == ownerId }?.let(::listOf).orEmpty()

        override fun findActiveBySourcePurchaseId(sourcePurchaseId: UUID): OwnershipRecord? =
            ownership?.takeIf { it.sourcePurchaseId == sourcePurchaseId }

        override fun findActiveOverlapping(range: TimeRange): List<OwnershipRecord> = emptyList()
    }
}
