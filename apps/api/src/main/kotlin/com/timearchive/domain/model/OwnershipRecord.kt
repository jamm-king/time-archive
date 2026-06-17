package com.timearchive.domain.model

import java.time.Instant
import java.util.UUID

data class OwnershipRecord(
    val id: UUID,
    val range: TimeRange,
    val ownerId: UUID,
    val status: OwnershipStatus,
    val validFrom: Instant,
    val validUntil: Instant?,
    val acquisitionType: AcquisitionType,
    val sourcePurchaseId: UUID?,
    val sourceTransactionId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        ArchiveTimeline.requireWithin(range)
        require(status != OwnershipStatus.ACTIVE || validUntil == null) {
            "active ownership must not have validUntil"
        }
        require(status == OwnershipStatus.ACTIVE || validUntil != null) {
            "inactive ownership must have validUntil"
        }
    }

    val isActive: Boolean
        get() = status == OwnershipStatus.ACTIVE && validUntil == null

    companion object {
        fun active(
            id: UUID,
            range: TimeRange,
            ownerId: UUID,
            validFrom: Instant,
            acquisitionType: AcquisitionType,
            sourcePurchaseId: UUID? = null,
            sourceTransactionId: UUID? = null,
        ): OwnershipRecord =
            OwnershipRecord(
                id = id,
                range = range,
                ownerId = ownerId,
                status = OwnershipStatus.ACTIVE,
                validFrom = validFrom,
                validUntil = null,
                acquisitionType = acquisitionType,
                sourcePurchaseId = sourcePurchaseId,
                sourceTransactionId = sourceTransactionId,
                createdAt = validFrom,
                updatedAt = validFrom,
            )
    }
}
