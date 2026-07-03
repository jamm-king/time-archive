package com.timearchive.application

import com.timearchive.domain.model.CheckoutAttempt
import com.timearchive.domain.model.CheckoutRequest
import com.timearchive.domain.model.CheckoutSession
import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.port.CheckoutAttemptRepository
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.PaymentPort
import com.timearchive.domain.port.PurchaseReservationRepository
import com.timearchive.domain.port.TransactionPort
import java.util.UUID

class CreateCheckout(
    private val transactionPort: TransactionPort,
    private val purchaseReservationRepository: PurchaseReservationRepository,
    private val checkoutAttemptRepository: CheckoutAttemptRepository,
    private val paymentPort: PaymentPort,
    private val clockPort: ClockPort,
) {
    fun create(command: Command): CheckoutSession {
        val prepared = prepareCheckout(command)
        val existingSession = prepared.attempt.toCheckoutSessionOrNull()
        if (existingSession != null) {
            return existingSession
        }

        return try {
            val checkout = paymentPort.createCheckout(
                CheckoutRequest(
                    reservationId = prepared.reservation.id,
                    checkoutAttemptId = prepared.attempt.id,
                    providerRequestId = prepared.attempt.providerRequestId,
                    buyerId = prepared.reservation.buyerId,
                    range = prepared.reservation.range,
                    amountCents = prepared.reservation.amountCents,
                    currency = prepared.reservation.currency,
                ),
            )
            recordProviderCreated(prepared.attempt, checkout)
            checkout
        } catch (exception: RuntimeException) {
            recordProviderFailed(prepared.attempt)
            throw exception
        }
    }

    private fun prepareCheckout(command: Command): PreparedCheckout =
        transactionPort.execute {
            val now = clockPort.now()
            val reservation = purchaseReservationRepository.findByIdForUpdate(command.reservationId)
                ?: error("purchase reservation not found")

            require(reservation.buyerId == command.currentUserId) {
                "reservation is not owned by current user"
            }
            require(reservation.status in setOf(PurchaseReservationStatus.HELD, PurchaseReservationStatus.CHECKOUT_CREATED)) {
                "reservation is not checkout eligible"
            }
            require(!reservation.isExpiredAt(now)) {
                "reservation is expired"
            }

            if (reservation.status == PurchaseReservationStatus.HELD) {
                val updated = purchaseReservationRepository.markCheckoutCreated(reservation.id, now)
                require(updated == 1) { "checkout status transition failed" }
            }

            val attempt = checkoutAttemptRepository.findByReservationIdForUpdate(reservation.id)
                ?: checkoutAttemptRepository.save(
                    CheckoutAttempt.pending(
                        id = UUID.randomUUID(),
                        reservation = reservation,
                        provider = paymentPort.provider,
                        providerRequestId = UUID.randomUUID().toString(),
                        now = now,
                    ),
                )

            require(attempt.provider == paymentPort.provider) {
                "checkout provider mismatch"
            }

            PreparedCheckout(
                reservation = reservation,
                attempt = attempt,
            )
        }

    private fun recordProviderCreated(
        attempt: CheckoutAttempt,
        checkout: CheckoutSession,
    ): Unit =
        transactionPort.execute {
            val updated = checkoutAttemptRepository.markProviderCreated(
                id = attempt.id,
                providerReference = checkout.providerReference,
                checkoutUrl = checkout.checkoutUrl,
                now = clockPort.now(),
            )
            require(updated == 1) { "checkout provider result transition failed" }
        }

    private fun recordProviderFailed(attempt: CheckoutAttempt): Unit =
        transactionPort.execute {
            checkoutAttemptRepository.markProviderFailed(
                id = attempt.id,
                now = clockPort.now(),
            )
        }

    data class Command(
        val currentUserId: UUID,
        val reservationId: UUID,
    )

    private data class PreparedCheckout(
        val reservation: PurchaseReservation,
        val attempt: CheckoutAttempt,
    )
}
