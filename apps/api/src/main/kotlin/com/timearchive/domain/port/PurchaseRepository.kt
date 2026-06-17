package com.timearchive.domain.port

import com.timearchive.domain.model.Purchase
import java.util.UUID

interface PurchaseRepository {
    fun save(purchase: Purchase): Purchase

    fun findByReservationId(reservationId: UUID): Purchase?
}
