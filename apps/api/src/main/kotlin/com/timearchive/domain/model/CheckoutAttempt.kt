package com.timearchive.domain.model

import java.time.Instant
import java.util.UUID

data class CheckoutAttempt(
    val id: UUID,
    val reservationId: UUID,
    val buyerId: UUID,
    val provider: String,
    val providerRequestId: String,
    val providerReference: String?,
    val checkoutUrl: String?,
    val captureRequestId: String?,
    val captureReference: String?,
    val capturedAt: Instant?,
    val status: CheckoutAttemptStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun toCheckoutSessionOrNull(): CheckoutSession? {
        if (status != CheckoutAttemptStatus.PROVIDER_CREATED) {
            return null
        }

        return CheckoutSession(
            provider = provider,
            providerReference = requireNotNull(providerReference),
            checkoutUrl = requireNotNull(checkoutUrl),
        )
    }

    companion object {
        fun pending(
            id: UUID,
            reservation: PurchaseReservation,
            provider: String,
            providerRequestId: String,
            now: Instant,
        ): CheckoutAttempt =
            CheckoutAttempt(
                id = id,
                reservationId = reservation.id,
                buyerId = reservation.buyerId,
                provider = provider,
                providerRequestId = providerRequestId,
                providerReference = null,
                checkoutUrl = null,
                captureRequestId = null,
                captureReference = null,
                capturedAt = null,
                status = CheckoutAttemptStatus.PENDING_PROVIDER,
                createdAt = now,
                updatedAt = now,
            )
    }
}
