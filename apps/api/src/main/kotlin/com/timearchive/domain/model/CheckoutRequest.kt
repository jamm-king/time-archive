package com.timearchive.domain.model

import java.util.UUID

data class CheckoutRequest(
    val reservationId: UUID,
    val buyerId: UUID,
    val range: TimeRange,
    val amountCents: Long,
    val currency: String,
) {
    init {
        ArchiveTimeline.requireWithin(range)
        require(amountCents > 0) { "amountCents must be greater than 0" }
        require(currency == PurchaseReservation.USD) { "currency must be USD" }
    }
}
