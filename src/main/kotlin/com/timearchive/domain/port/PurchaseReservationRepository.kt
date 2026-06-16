package com.timearchive.domain.port

import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.TimeRange
import java.time.Instant

interface PurchaseReservationRepository {
    fun save(reservation: PurchaseReservation): PurchaseReservation

    fun findActiveOverlapping(range: TimeRange): List<PurchaseReservation>

    fun expireOverdue(now: Instant): Int
}
