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
        require(expiresAt.isAfter(createdAt)) { "expiresAt must be after createdAt" }
        require(!updatedAt.isBefore(createdAt)) { "updatedAt must not be before createdAt" }
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
                expiresAt = expiresAt,
                createdAt = now,
                updatedAt = now,
            )
    }
}
