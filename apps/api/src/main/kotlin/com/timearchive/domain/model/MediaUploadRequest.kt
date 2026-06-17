package com.timearchive.domain.model

import java.time.Instant
import java.util.UUID

data class MediaUploadRequest(
    val id: UUID,
    val ownershipRecordId: UUID,
    val ownerId: UUID,
    val mediaType: MediaType,
    val originalFilename: String,
    val contentType: String,
    val contentLengthBytes: Long,
    val objectKey: String,
    val originalFileUrl: String,
    val status: MediaUploadRequestStatus,
    val mediaAssetId: UUID?,
    val expiresAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(originalFilename.isNotBlank()) { "originalFilename must not be blank" }
        require(contentType.isNotBlank()) { "contentType must not be blank" }
        require(contentLengthBytes > 0) { "contentLengthBytes must be greater than 0" }
        require(objectKey.isNotBlank()) { "objectKey must not be blank" }
        require(originalFileUrl.isNotBlank()) { "originalFileUrl must not be blank" }
        require(status != MediaUploadRequestStatus.COMPLETED || mediaAssetId != null) {
            "completed upload request must have mediaAssetId"
        }
        require(status == MediaUploadRequestStatus.COMPLETED || mediaAssetId == null) {
            "incomplete upload request must not have mediaAssetId"
        }
        require(expiresAt.isAfter(createdAt)) { "expiresAt must be after createdAt" }
        require(!updatedAt.isBefore(createdAt)) { "updatedAt must not be before createdAt" }
    }

    fun complete(
        mediaAssetId: UUID,
        now: Instant,
    ): MediaUploadRequest {
        require(status == MediaUploadRequestStatus.REQUESTED) {
            "upload request is not completable"
        }
        require(!now.isAfter(expiresAt)) { "upload request is expired" }

        return copy(
            status = MediaUploadRequestStatus.COMPLETED,
            mediaAssetId = mediaAssetId,
            updatedAt = now,
        )
    }

    companion object {
        fun requested(
            id: UUID,
            ownershipRecordId: UUID,
            ownerId: UUID,
            mediaType: MediaType,
            originalFilename: String,
            contentType: String,
            contentLengthBytes: Long,
            objectKey: String,
            originalFileUrl: String,
            now: Instant,
            expiresAt: Instant,
        ): MediaUploadRequest =
            MediaUploadRequest(
                id = id,
                ownershipRecordId = ownershipRecordId,
                ownerId = ownerId,
                mediaType = mediaType,
                originalFilename = originalFilename,
                contentType = contentType,
                contentLengthBytes = contentLengthBytes,
                objectKey = objectKey,
                originalFileUrl = originalFileUrl,
                status = MediaUploadRequestStatus.REQUESTED,
                mediaAssetId = null,
                expiresAt = expiresAt,
                createdAt = now,
                updatedAt = now,
            )
    }
}
