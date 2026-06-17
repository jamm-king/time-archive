package com.timearchive.adapter.inbound.rest

import com.timearchive.domain.model.CheckoutSession
import com.timearchive.domain.model.PurchaseReservation
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateReservationRequest(
    @field:NotNull
    val buyerId: UUID?,
    @field:NotNull
    @field:Min(0)
    @field:Max(86_399)
    val startSecond: Long?,
    @field:NotNull
    @field:Min(1)
    @field:Max(86_400)
    val endSecond: Long?,
)

data class ReservationResponse(
    val reservationId: UUID,
    val buyerId: UUID,
    val startSecond: Long,
    val endSecond: Long,
    val amountCents: Long,
    val currency: String,
    val status: String,
    val expiresAt: Instant,
) {
    companion object {
        fun from(reservation: PurchaseReservation): ReservationResponse =
            ReservationResponse(
                reservationId = reservation.id,
                buyerId = reservation.buyerId,
                startSecond = reservation.range.startSecond,
                endSecond = reservation.range.endSecond,
                amountCents = reservation.amountCents,
                currency = reservation.currency,
                status = reservation.status.name,
                expiresAt = reservation.expiresAt,
            )
    }
}

data class CheckoutSessionResponse(
    val provider: String,
    val providerReference: String,
    val checkoutUrl: String,
) {
    companion object {
        fun from(session: CheckoutSession): CheckoutSessionResponse =
            CheckoutSessionResponse(
                provider = session.provider,
                providerReference = session.providerReference,
                checkoutUrl = session.checkoutUrl,
            )
    }
}
