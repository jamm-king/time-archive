package com.timearchive.application

import com.timearchive.domain.model.ArchiveTimeline
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.PurchaseReservationRepository

class CheckAvailability(
    private val ownershipRepository: OwnershipRepository,
    private val purchaseReservationRepository: PurchaseReservationRepository,
    private val clockPort: ClockPort,
) {
    fun check(query: Query): Result {
        val range = TimeRange(
            startSecond = query.startSecond,
            endSecond = query.endSecond,
        )
        ArchiveTimeline.requireWithin(range)

        val now = clockPort.now()
        purchaseReservationRepository.expireOverdue(now)

        val ownershipConflicts = ownershipRepository
            .findActiveOverlapping(range)
            .map { Conflict(type = ConflictType.OWNERSHIP, range = it.range) }
        val reservationConflicts = purchaseReservationRepository
            .findActiveOverlapping(range)
            .map { Conflict(type = ConflictType.RESERVATION, range = it.range) }
        val conflicts = ownershipConflicts + reservationConflicts

        return Result(
            range = range,
            available = conflicts.isEmpty(),
            conflicts = conflicts,
        )
    }

    data class Query(
        val startSecond: Long,
        val endSecond: Long,
    )

    data class Result(
        val range: TimeRange,
        val available: Boolean,
        val conflicts: List<Conflict>,
    )

    data class Conflict(
        val type: ConflictType,
        val range: TimeRange,
    )

    enum class ConflictType {
        OWNERSHIP,
        RESERVATION,
    }
}
