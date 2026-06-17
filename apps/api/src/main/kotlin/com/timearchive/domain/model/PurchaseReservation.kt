package com.timearchive.domain.model

import java.time.Instant
import java.util.UUID

data class PurchaseReservation(
    val id: UUID,
    val buyerId: UUID,
    val range: TimeRange,
    val amountCents: Long,
    val currency: String,
    val status: PurchaseReservationStatus,
    val expiresAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        ArchiveTimeline.requireWithin(range)
        require(amountCents > 0) { "amountCents must be greater than 0" }
        require(currency == USD) { "currency must be USD" }
    }

    val isActive: Boolean
        get() = status == PurchaseReservationStatus.HELD ||
            status == PurchaseReservationStatus.CHECKOUT_CREATED

    fun isExpiredAt(now: Instant): Boolean = expiresAt <= now

    companion object {
        const val USD: String = "USD"
        const val CENTS_PER_SECOND: Long = 100

        fun held(
            id: UUID,
            buyerId: UUID,
            range: TimeRange,
            now: Instant,
            expiresAt: Instant,
        ): PurchaseReservation {
            require(expiresAt > now) { "expiresAt must be after now" }

            return PurchaseReservation(
                id = id,
                buyerId = buyerId,
                range = range,
                amountCents = range.durationSeconds * CENTS_PER_SECOND,
                currency = USD,
                status = PurchaseReservationStatus.HELD,
                expiresAt = expiresAt,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
