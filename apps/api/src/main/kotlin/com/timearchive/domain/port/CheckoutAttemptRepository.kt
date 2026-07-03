package com.timearchive.domain.port

import com.timearchive.domain.model.CheckoutAttempt
import java.time.Instant
import java.util.UUID

interface CheckoutAttemptRepository {
    fun save(attempt: CheckoutAttempt): CheckoutAttempt

    fun findByReservationIdForUpdate(reservationId: UUID): CheckoutAttempt?

    fun markProviderCreated(
        id: UUID,
        providerReference: String,
        checkoutUrl: String,
        now: Instant,
    ): Int

    fun markProviderFailed(
        id: UUID,
        now: Instant,
    ): Int
}
