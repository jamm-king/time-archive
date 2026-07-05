package com.timearchive.domain.port

import com.timearchive.domain.model.CheckoutAttempt
import java.time.Instant
import java.util.UUID

interface CheckoutAttemptRepository {
    fun save(attempt: CheckoutAttempt): CheckoutAttempt

    fun findByReservationIdForUpdate(reservationId: UUID): CheckoutAttempt?

    fun findByProviderReference(provider: String, providerReference: String): CheckoutAttempt? =
        findByProviderReferenceForUpdate(provider, providerReference)

    fun findByProviderReferenceForUpdate(provider: String, providerReference: String): CheckoutAttempt?

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

    fun markCaptureCompleted(
        id: UUID,
        captureRequestId: String,
        captureReference: String,
        capturedAt: Instant,
    ): Int

    fun markCaptureFailed(
        id: UUID,
        captureRequestId: String,
        now: Instant,
    ): Int
}
