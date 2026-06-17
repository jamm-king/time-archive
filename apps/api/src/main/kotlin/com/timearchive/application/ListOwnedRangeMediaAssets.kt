package com.timearchive.application

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.OwnershipRepository
import java.util.UUID

class ListOwnedRangeMediaAssets(
    private val ownershipRepository: OwnershipRepository,
    private val mediaAssetRepository: MediaAssetRepository,
) {
    fun list(query: Query): List<MediaAsset> {
        val ownership = ownershipRepository.findById(query.ownershipRecordId)
            ?: throw IllegalStateException("ownership record not found")

        require(ownership.isActive) { "ownership record is not active" }
        require(ownership.ownerId == query.currentUserId) {
            "ownership record is not owned by current user"
        }

        return mediaAssetRepository.findByOwnershipRecordId(ownership.id)
    }

    data class Query(
        val currentUserId: UUID,
        val ownershipRecordId: UUID,
    )
}
