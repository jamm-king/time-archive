package com.timearchive.domain.port

import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.TimeRange
import java.util.UUID

interface OwnershipRepository {
    fun save(record: OwnershipRecord): OwnershipRecord

    fun findById(id: UUID): OwnershipRecord?

    fun findActiveOverlapping(range: TimeRange): List<OwnershipRecord>
}
