package com.timearchive.domain.port

import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.TimeRange
import java.time.Instant
import java.util.UUID

interface PurchaseReservationRepository {
    fun save(reservation: PurchaseReservation): PurchaseReservation

    fun findByIdForUpdate(id: UUID): PurchaseReservation?

    fun findActiveOverlapping(range: TimeRange): List<PurchaseReservation>

    fun expireOverdue(now: Instant): Int

    fun markCompleted(id: UUID, now: Instant): Int
}
