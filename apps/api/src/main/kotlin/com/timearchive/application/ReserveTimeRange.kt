package com.timearchive.application

import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.PurchaseReservationRepository
import java.time.Duration
import java.util.UUID

class ReserveTimeRange(
    private val ownershipRepository: OwnershipRepository,
    private val purchaseReservationRepository: PurchaseReservationRepository,
    private val clockPort: ClockPort,
    private val reservationDuration: Duration = Duration.ofMinutes(10),
    private val idGenerator: () -> UUID = UUID::randomUUID,
) {
    fun reserve(command: Command): PurchaseReservation {
        val range = TimeRange(
            startSecond = command.startSecond,
            endSecond = command.endSecond,
        )
        val now = clockPort.now()

        purchaseReservationRepository.expireOverdue(now)

        require(ownershipRepository.findActiveOverlapping(range).isEmpty()) {
            "time range already has active ownership"
        }
        require(purchaseReservationRepository.findActiveOverlapping(range).isEmpty()) {
            "time range already has active reservation"
        }

        return purchaseReservationRepository.save(
            PurchaseReservation.held(
                id = idGenerator(),
                buyerId = command.buyerId,
                range = range,
                now = now,
                expiresAt = now.plus(reservationDuration),
            ),
        )
    }

    data class Command(
        val buyerId: UUID,
        val startSecond: Long,
        val endSecond: Long,
    )
}
