package com.timearchive.application

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.TransactionPort
import java.util.UUID

class CreateOwnedRangeMediaAsset(
    private val transactionPort: TransactionPort,
    private val ownershipRepository: OwnershipRepository,
    private val mediaAssetRepository: MediaAssetRepository,
    private val clockPort: ClockPort,
    private val idGenerator: () -> UUID = UUID::randomUUID,
) {
    fun create(command: Command): MediaAsset =
        transactionPort.execute {
            val ownership = ownershipRepository.findById(command.ownershipRecordId)
                ?: throw IllegalStateException("ownership record not found")

            require(ownership.isActive) { "ownership record is not active" }
            require(ownership.ownerId == command.currentUserId) {
                "ownership record is not owned by current user"
            }

            mediaAssetRepository.save(
                MediaAsset.uploaded(
                    id = idGenerator(),
                    ownershipRecordId = ownership.id,
                    ownerId = ownership.ownerId,
                    mediaType = command.mediaType,
                    originalFileUrl = command.originalFileUrl,
                    thumbnailUrl = command.thumbnailUrl,
                    externalLink = command.externalLink,
                    now = clockPort.now(),
                ),
            )
        }

    data class Command(
        val currentUserId: UUID,
        val ownershipRecordId: UUID,
        val mediaType: MediaType,
        val originalFileUrl: String,
        val thumbnailUrl: String?,
        val externalLink: String?,
    )
}
