package com.timearchive.domain.model

import java.time.Instant
import java.util.UUID

data class Purchase(
    val id: UUID,
    val buyerId: UUID,
    val reservationId: UUID,
    val range: TimeRange,
    val amountCents: Long,
    val currency: String,
    val status: PurchaseStatus,
    val paymentProvider: String?,
    val paymentReference: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        ArchiveTimeline.requireWithin(range)
        require(amountCents > 0) { "amountCents must be greater than 0" }
        require(currency == PurchaseReservation.USD) { "currency must be USD" }
    }

    companion object {
        fun ownershipGranted(
            id: UUID,
            reservation: PurchaseReservation,
            paymentProvider: String,
            paymentReference: String,
            now: Instant,
        ): Purchase =
            Purchase(
                id = id,
                buyerId = reservation.buyerId,
                reservationId = reservation.id,
                range = reservation.range,
                amountCents = reservation.amountCents,
                currency = reservation.currency,
                status = PurchaseStatus.OWNERSHIP_GRANTED,
                paymentProvider = paymentProvider,
                paymentReference = paymentReference,
                createdAt = now,
                updatedAt = now,
            )
    }
}
