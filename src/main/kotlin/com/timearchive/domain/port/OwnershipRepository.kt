package com.timearchive.domain.port

import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.TimeRange

interface OwnershipRepository {
    fun save(record: OwnershipRecord): OwnershipRecord

    fun findActiveOverlapping(range: TimeRange): List<OwnershipRecord>
}
