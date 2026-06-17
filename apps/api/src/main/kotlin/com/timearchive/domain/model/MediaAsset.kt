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

    fun approve(
        approvedFileUrl: String,
        thumbnailUrl: String?,
        now: Instant,
    ): MediaAsset {
        require(moderationStatus == ModerationStatus.UPLOADED || moderationStatus == ModerationStatus.PENDING_REVIEW) {
            "media asset is not approvable"
        }

        return copy(
            approvedFileUrl = approvedFileUrl,
            thumbnailUrl = thumbnailUrl ?: this.thumbnailUrl,
            moderationStatus = ModerationStatus.APPROVED,
            updatedAt = now,
        )
    }

    fun reject(now: Instant): MediaAsset {
        require(moderationStatus == ModerationStatus.UPLOADED || moderationStatus == ModerationStatus.PENDING_REVIEW) {
            "media asset is not rejectable"
        }

        return copy(
            moderationStatus = ModerationStatus.REJECTED,
            updatedAt = now,
        )
    }

    fun hide(now: Instant): MediaAsset {
        require(moderationStatus == ModerationStatus.APPROVED) {
            "media asset is not hideable"
        }

        return copy(
            moderationStatus = ModerationStatus.HIDDEN,
            updatedAt = now,
        )
    }

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
