package com.timearchive.adapter.inbound.rest

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateOwnedRangeMediaRequest(
    @field:NotNull
    val mediaType: MediaType?,
    @field:NotBlank
    val originalFileUrl: String?,
    val thumbnailUrl: String?,
    val externalLink: String?,
)

data class MediaAssetResponse(
    val mediaAssetId: UUID,
    val ownershipRecordId: UUID,
    val ownerId: UUID,
    val mediaType: String,
    val originalFileUrl: String,
    val approvedFileUrl: String?,
    val thumbnailUrl: String?,
    val externalLink: String?,
    val moderationStatus: String,
    val publiclyVisible: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(asset: MediaAsset): MediaAssetResponse =
            MediaAssetResponse(
                mediaAssetId = asset.id,
                ownershipRecordId = asset.ownershipRecordId,
                ownerId = asset.ownerId,
                mediaType = asset.mediaType.name,
                originalFileUrl = asset.originalFileUrl,
                approvedFileUrl = asset.approvedFileUrl,
                thumbnailUrl = asset.thumbnailUrl,
                externalLink = asset.externalLink,
                moderationStatus = asset.moderationStatus.name,
                publiclyVisible = asset.isPubliclyVisible,
                createdAt = asset.createdAt,
                updatedAt = asset.updatedAt,
            )
    }
}
