package com.timearchive.adapter.inbound.rest

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import com.timearchive.application.CreateOwnedRangeMediaUploadRequest
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
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

data class CreateMediaUploadRequest(
    @field:NotNull
    val mediaType: MediaType?,
    @field:NotBlank
    val originalFilename: String?,
    @field:NotBlank
    val contentType: String?,
    @field:NotNull
    @field:Positive
    val contentLengthBytes: Long?,
)

data class MediaUploadRequestResponse(
    val uploadRequestId: UUID,
    val ownershipRecordId: UUID,
    val ownerId: UUID,
    val mediaType: String,
    val originalFilename: String,
    val contentType: String,
    val contentLengthBytes: Long,
    val originalFileUrl: String,
    val uploadUrl: String,
    val requiredHeaders: Map<String, String>,
    val status: String,
    val expiresAt: Instant,
    val createdAt: Instant,
) {
    companion object {
        fun from(result: CreateOwnedRangeMediaUploadRequest.Result): MediaUploadRequestResponse {
            val uploadRequest = result.uploadRequest
            return MediaUploadRequestResponse(
                uploadRequestId = uploadRequest.id,
                ownershipRecordId = uploadRequest.ownershipRecordId,
                ownerId = uploadRequest.ownerId,
                mediaType = uploadRequest.mediaType.name,
                originalFilename = uploadRequest.originalFilename,
                contentType = uploadRequest.contentType,
                contentLengthBytes = uploadRequest.contentLengthBytes,
                originalFileUrl = uploadRequest.originalFileUrl,
                uploadUrl = result.uploadUrl,
                requiredHeaders = result.requiredHeaders,
                status = uploadRequest.status.name,
                expiresAt = uploadRequest.expiresAt,
                createdAt = uploadRequest.createdAt,
            )
        }
    }
}
