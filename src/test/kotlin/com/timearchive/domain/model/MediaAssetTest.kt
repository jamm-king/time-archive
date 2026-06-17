package com.timearchive.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MediaAssetTest {
    private val now: Instant = Instant.parse("2026-06-17T00:00:00Z")

    @Test
    fun `creates uploaded media asset`() {
        val asset = MediaAsset.uploaded(
            id = UUID.randomUUID(),
            ownershipRecordId = UUID.randomUUID(),
            ownerId = UUID.randomUUID(),
            mediaType = MediaType.IMAGE,
            originalFileUrl = "s3://bucket/original/image.png",
            thumbnailUrl = "s3://bucket/thumb/image.png",
            externalLink = "https://example.com",
            now = now,
        )

        assertThat(asset.mediaType).isEqualTo(MediaType.IMAGE)
        assertThat(asset.moderationStatus).isEqualTo(ModerationStatus.UPLOADED)
        assertThat(asset.approvedFileUrl).isNull()
        assertThat(asset.isPubliclyVisible).isFalse()
        assertThat(asset.createdAt).isEqualTo(now)
        assertThat(asset.updatedAt).isEqualTo(now)
    }

    @Test
    fun `approved media is publicly visible`() {
        val asset = mediaAsset(
            approvedFileUrl = "s3://bucket/approved/image.png",
            moderationStatus = ModerationStatus.APPROVED,
        )

        assertThat(asset.isPubliclyVisible).isTrue()
    }

    @Test
    fun `rejects blank original file url`() {
        assertThatIllegalArgumentException()
            .isThrownBy { mediaAsset(originalFileUrl = " ") }
            .withMessage("originalFileUrl must not be blank")
    }

    @Test
    fun `rejects blank approved file url`() {
        assertThatIllegalArgumentException()
            .isThrownBy { mediaAsset(approvedFileUrl = " ") }
            .withMessage("approvedFileUrl must not be blank")
    }

    @Test
    fun `rejects blank thumbnail url`() {
        assertThatIllegalArgumentException()
            .isThrownBy { mediaAsset(thumbnailUrl = " ") }
            .withMessage("thumbnailUrl must not be blank")
    }

    @Test
    fun `rejects blank external link`() {
        assertThatIllegalArgumentException()
            .isThrownBy { mediaAsset(externalLink = " ") }
            .withMessage("externalLink must not be blank")
    }

    @Test
    fun `rejects approved media without approved file url`() {
        assertThatIllegalArgumentException()
            .isThrownBy { mediaAsset(moderationStatus = ModerationStatus.APPROVED) }
            .withMessage("approved media must have approvedFileUrl")
    }

    @Test
    fun `rejects updatedAt before createdAt`() {
        assertThatIllegalArgumentException()
            .isThrownBy {
                mediaAsset(
                    createdAt = now,
                    updatedAt = now.minusSeconds(1),
                )
            }
            .withMessage("updatedAt must not be before createdAt")
    }

    private fun mediaAsset(
        originalFileUrl: String = "s3://bucket/original/image.png",
        approvedFileUrl: String? = null,
        thumbnailUrl: String? = null,
        externalLink: String? = null,
        moderationStatus: ModerationStatus = ModerationStatus.UPLOADED,
        createdAt: Instant = now,
        updatedAt: Instant = now,
    ): MediaAsset =
        MediaAsset(
            id = UUID.randomUUID(),
            ownershipRecordId = UUID.randomUUID(),
            ownerId = UUID.randomUUID(),
            mediaType = MediaType.IMAGE,
            originalFileUrl = originalFileUrl,
            approvedFileUrl = approvedFileUrl,
            thumbnailUrl = thumbnailUrl,
            externalLink = externalLink,
            moderationStatus = moderationStatus,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
