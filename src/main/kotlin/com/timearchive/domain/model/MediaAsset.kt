package com.timearchive.domain.model

import java.time.Instant
import java.util.UUID

data class MediaAsset(
    val id: UUID,
    val ownershipRecordId: UUID,
    val ownerId: UUID,
    val mediaType: MediaType,
    val originalFileUrl: String,
    val approvedFileUrl: String?,
    val thumbnailUrl: String?,
    val externalLink: String?,
    val moderationStatus: ModerationStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(originalFileUrl.isNotBlank()) { "originalFileUrl must not be blank" }
        require(approvedFileUrl == null || approvedFileUrl.isNotBlank()) {
            "approvedFileUrl must not be blank"
        }
        require(thumbnailUrl == null || thumbnailUrl.isNotBlank()) {
            "thumbnailUrl must not be blank"
        }
        require(externalLink == null || externalLink.isNotBlank()) {
            "externalLink must not be blank"
        }
        require(moderationStatus != ModerationStatus.APPROVED || approvedFileUrl != null) {
            "approved media must have approvedFileUrl"
        }
        require(!updatedAt.isBefore(createdAt)) { "updatedAt must not be before createdAt" }
    }

    val isPubliclyVisible: Boolean
        get() = moderationStatus == ModerationStatus.APPROVED

    companion object {
        fun uploaded(
            id: UUID,
            ownershipRecordId: UUID,
            ownerId: UUID,
            mediaType: MediaType,
            originalFileUrl: String,
            thumbnailUrl: String? = null,
            externalLink: String? = null,
            now: Instant,
        ): MediaAsset =
            MediaAsset(
                id = id,
                ownershipRecordId = ownershipRecordId,
                ownerId = ownerId,
                mediaType = mediaType,
                originalFileUrl = originalFileUrl,
                approvedFileUrl = null,
                thumbnailUrl = thumbnailUrl,
                externalLink = externalLink,
                moderationStatus = ModerationStatus.UPLOADED,
                createdAt = now,
                updatedAt = now,
            )
    }
}
