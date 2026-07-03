package com.timearchive.application

import com.timearchive.adapter.outbound.payment.PayPalCaptureOrderCommand
import com.timearchive.adapter.outbound.payment.PayPalOrderClient
import com.timearchive.domain.model.CheckoutAttempt
import com.timearchive.domain.model.CheckoutAttemptStatus
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.port.CheckoutAttemptRepository
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.PurchaseReservationRepository
import com.timearchive.domain.port.TransactionPort
import java.util.UUID

class CapturePayPalOrder(
    private val transactionPort: TransactionPort,
    private val checkoutAttemptRepository: CheckoutAttemptRepository,
    private val purchaseReservationRepository: PurchaseReservationRepository,
    private val paypalOrderClient: PayPalOrderClient,
    private val clockPort: ClockPort,
) {
    fun capture(command: Command): Result {
        val prepared = prepareCapture(command)
        if (prepared.attempt.status == CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK) {
            return Result(
                orderId = command.orderId,
                captureReference = requireNotNull(prepared.attempt.captureReference),
                status = "CAPTURED_PENDING_WEBHOOK",
                alreadyCaptured = true,
            )
        }

        return try {
            val capture = paypalOrderClient.captureOrder(
                PayPalCaptureOrderCommand(
                    providerRequestId = prepared.captureRequestId,
                    orderId = command.orderId,
                ),
            )
            require(capture.status == "COMPLETED") { "paypal capture did not complete" }
            recordCaptureCompleted(
                attempt = prepared.attempt,
                captureRequestId = prepared.captureRequestId,
                captureReference = capture.captureId,
            )
            Result(
                orderId = capture.orderId,
                captureReference = capture.captureId,
                status = "CAPTURED_PENDING_WEBHOOK",
                alreadyCaptured = false,
            )
        } catch (exception: RuntimeException) {
            recordCaptureFailed(prepared.attempt, prepared.captureRequestId)
            throw exception
        }
    }

    private fun prepareCapture(command: Command): PreparedCapture =
        transactionPort.execute {
            val now = clockPort.now()
            val attempt = checkoutAttemptRepository.findByProviderReferenceForUpdate(
                provider = "paypal",
                providerReference = command.orderId,
            ) ?: error("checkout attempt not found")

            require(attempt.buyerId == command.currentUserId) {
                "checkout attempt is not owned by current user"
            }
            require(
                attempt.status in setOf(
                    CheckoutAttemptStatus.PROVIDER_CREATED,
                    CheckoutAttemptStatus.CAPTURE_FAILED,
                    CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK,
                ),
            ) {
                "checkout attempt is not capturable"
            }

            val reservation = purchaseReservationRepository.findByIdForUpdate(attempt.reservationId)
                ?: error("purchase reservation not found")

            require(reservation.status == PurchaseReservationStatus.CHECKOUT_CREATED) {
                "reservation is not payable"
            }
            require(!reservation.isExpiredAt(now)) {
                "reservation is expired"
            }

            PreparedCapture(
                attempt = attempt,
                captureRequestId = attempt.captureRequestId ?: "capture-${attempt.id}",
            )
        }

    private fun recordCaptureCompleted(
        attempt: CheckoutAttempt,
        captureRequestId: String,
        captureReference: String,
    ) {
        transactionPort.execute {
            val updated = checkoutAttemptRepository.markCaptureCompleted(
                id = attempt.id,
                captureRequestId = captureRequestId,
                captureReference = captureReference,
                capturedAt = clockPort.now(),
            )
            require(updated == 1) { "payment capture transition failed" }
        }
    }

    private fun recordCaptureFailed(
        attempt: CheckoutAttempt,
        captureRequestId: String,
    ) {
        transactionPort.execute {
            checkoutAttemptRepository.markCaptureFailed(
                id = attempt.id,
                captureRequestId = captureRequestId,
                now = clockPort.now(),
            )
        }
    }

    data class Command(
        val currentUserId: UUID,
        val orderId: String,
    )

    data class Result(
        val orderId: String,
        val captureReference: String,
        val status: String,
        val alreadyCaptured: Boolean,
    )

    private data class PreparedCapture(
        val attempt: CheckoutAttempt,
        val captureRequestId: String,
    )
}
