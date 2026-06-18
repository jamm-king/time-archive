package com.timearchive.adapter.inbound.rest

import com.timearchive.domain.model.OwnershipRecord
import java.time.Instant
import java.util.UUID

data class CurrentUserOwnedRangeResponse(
    val ownershipRecordId: UUID,
    val startSecond: Long,
    val endSecond: Long,
    val status: String,
    val acquiredAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(record: OwnershipRecord): CurrentUserOwnedRangeResponse =
            CurrentUserOwnedRangeResponse(
                ownershipRecordId = record.id,
                startSecond = record.range.startSecond,
                endSecond = record.range.endSecond,
                status = record.status.name,
                acquiredAt = record.validFrom,
                createdAt = record.createdAt,
                updatedAt = record.updatedAt,
            )
    }
}
