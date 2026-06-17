package com.timearchive.application

import com.timearchive.domain.model.CheckoutRequest
import com.timearchive.domain.model.CheckoutSession
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.PaymentPort
import com.timearchive.domain.port.PurchaseReservationRepository
import com.timearchive.domain.port.TransactionPort
import java.util.UUID

class CreateCheckout(
    private val transactionPort: TransactionPort,
    private val purchaseReservationRepository: PurchaseReservationRepository,
    private val paymentPort: PaymentPort,
    private val clockPort: ClockPort,
) {
    fun create(command: Command): CheckoutSession =
        transactionPort.execute {
            val now = clockPort.now()
            val reservation = purchaseReservationRepository.findByIdForUpdate(command.reservationId)
                ?: error("purchase reservation not found")

            require(reservation.status == PurchaseReservationStatus.HELD) {
                "reservation is not held"
            }
            require(!reservation.isExpiredAt(now)) {
                "reservation is expired"
            }

            val checkout = paymentPort.createCheckout(
                CheckoutRequest(
                    reservationId = reservation.id,
                    buyerId = reservation.buyerId,
                    range = reservation.range,
                    amountCents = reservation.amountCents,
                    currency = reservation.currency,
                ),
            )

            val updated = purchaseReservationRepository.markCheckoutCreated(reservation.id, now)
            require(updated == 1) { "checkout status transition failed" }

            checkout
        }

    data class Command(
        val reservationId: UUID,
    )
}
