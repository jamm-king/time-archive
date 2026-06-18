package com.timearchive.application

import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.port.OwnershipRepository
import java.util.UUID

class ListCurrentUserOwnedRanges(
    private val ownershipRepository: OwnershipRepository,
) {
    fun list(query: Query): List<OwnershipRecord> =
        ownershipRepository.findActiveByOwnerId(query.currentUserId)

    data class Query(
        val currentUserId: UUID,
    )
}
