package com.timearchive.application

import com.timearchive.domain.model.CheckoutAttemptStatus
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.model.PurchaseStatus
import com.timearchive.domain.port.CheckoutAttemptRepository
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.PurchaseRepository
import com.timearchive.domain.port.PurchaseReservationRepository
import java.util.UUID

class GetPayPalOrderConfirmationStatus(
    private val checkoutAttemptRepository: CheckoutAttemptRepository,
    private val purchaseReservationRepository: PurchaseReservationRepository,
    private val purchaseRepository: PurchaseRepository,
    private val ownershipRepository: OwnershipRepository,
    private val clockPort: ClockPort,
) {
    fun get(command: Command): Result {
        val attempt = checkoutAttemptRepository.findByProviderReference(
            provider = "paypal",
            providerReference = command.orderId,
        ) ?: error("checkout attempt not found")

        require(attempt.buyerId == command.currentUserId) {
            "checkout attempt is not owned by current user"
        }

        val reservation = purchaseReservationRepository.findById(attempt.reservationId)
            ?: error("purchase reservation not found")
        val purchase = purchaseRepository.findByReservationId(reservation.id)
        val ownership = purchase?.let { ownershipRepository.findActiveBySourcePurchaseId(it.id) }

        val status = when {
            purchase?.status == PurchaseStatus.OWNERSHIP_GRANTED && ownership != null ->
                ConfirmationStatus.OWNERSHIP_GRANTED
            purchase?.status == PurchaseStatus.EXPIRED ->
                ConfirmationStatus.EXPIRED
            purchase?.status in setOf(PurchaseStatus.FAILED, PurchaseStatus.REFUNDED) ->
                ConfirmationStatus.FAILED
            reservation.status == PurchaseReservationStatus.EXPIRED || reservation.isExpiredAt(clockPort.now()) ->
                ConfirmationStatus.EXPIRED
            attempt.status == CheckoutAttemptStatus.CAPTURE_FAILED ->
                ConfirmationStatus.CAPTURE_FAILED
            attempt.status == CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK ->
                ConfirmationStatus.CAPTURE_PENDING_WEBHOOK
            attempt.status == CheckoutAttemptStatus.PROVIDER_CREATED ->
                ConfirmationStatus.CAPTURE_NOT_STARTED
            else ->
                ConfirmationStatus.FAILED
        }

        return Result(
            orderId = command.orderId,
            reservationId = reservation.id,
            purchaseId = purchase?.id,
            ownershipRecordId = ownership?.id,
            status = status,
            terminal = status in setOf(
                ConfirmationStatus.OWNERSHIP_GRANTED,
                ConfirmationStatus.EXPIRED,
                ConfirmationStatus.FAILED,
                ConfirmationStatus.CAPTURE_FAILED,
            ),
        )
    }

    data class Command(
        val currentUserId: UUID,
        val orderId: String,
    )

    data class Result(
        val orderId: String,
        val reservationId: UUID,
        val purchaseId: UUID?,
        val ownershipRecordId: UUID?,
        val status: ConfirmationStatus,
        val terminal: Boolean,
    )

    enum class ConfirmationStatus {
        CAPTURE_NOT_STARTED,
        CAPTURE_PENDING_WEBHOOK,
        OWNERSHIP_GRANTED,
        CAPTURE_FAILED,
        EXPIRED,
        FAILED,
    }
}
