package com.timearchive.application

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.ModerationStatus
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.OwnershipRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ListOwnedRangeMediaAssetsTest {
    private val now: Instant = Instant.parse("2026-06-17T00:00:00Z")
    private val ownerId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000401")
    private val ownershipRecord: OwnershipRecord = activeOwnership(ownerId = ownerId)

    @Test
    fun `lists all media assets for active owned range`() {
        val asset = mediaAsset()
        val useCase = ListOwnedRangeMediaAssets(
            ownershipRepository = FakeOwnershipRepository(record = ownershipRecord),
            mediaAssetRepository = FakeMediaAssetRepository(assets = listOf(asset)),
        )

        val result = useCase.list(
            ListOwnedRangeMediaAssets.Query(
                currentUserId = ownerId,
                ownershipRecordId = ownershipRecord.id,
            ),
        )

        assertThat(result).containsExactly(asset)
    }

    @Test
    fun `rejects listing for missing ownership record`() {
        val useCase = ListOwnedRangeMediaAssets(
            ownershipRepository = FakeOwnershipRepository(record = null),
            mediaAssetRepository = FakeMediaAssetRepository(),
        )

        assertThatIllegalStateException()
            .isThrownBy {
                useCase.list(
                    ListOwnedRangeMediaAssets.Query(
                        currentUserId = ownerId,
                        ownershipRecordId = ownershipRecord.id,
                    ),
                )
            }
            .withMessage("ownership record not found")
    }

    @Test
    fun `rejects listing for another owner's range`() {
        val useCase = ListOwnedRangeMediaAssets(
            ownershipRepository = FakeOwnershipRepository(record = ownershipRecord),
            mediaAssetRepository = FakeMediaAssetRepository(),
        )

        assertThatIllegalArgumentException()
            .isThrownBy {
                useCase.list(
                    ListOwnedRangeMediaAssets.Query(
                        currentUserId = UUID.randomUUID(),
                        ownershipRecordId = ownershipRecord.id,
                    ),
                )
            }
            .withMessage("ownership record is not owned by current user")
    }

    private fun activeOwnership(ownerId: UUID): OwnershipRecord =
        OwnershipRecord.active(
            id = UUID.fromString("00000000-0000-0000-0000-000000000501"),
            range = TimeRange(startSecond = 20, endSecond = 21),
            ownerId = ownerId,
            validFrom = now,
            acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
        )

    private fun mediaAsset(): MediaAsset =
        MediaAsset.uploaded(
            id = UUID.fromString("00000000-0000-0000-0000-000000000601"),
            ownershipRecordId = ownershipRecord.id,
            ownerId = ownerId,
            mediaType = MediaType.VIDEO,
            originalFileUrl = "https://cdn.example.com/original.mp4",
            now = now,
        )

    private class FakeOwnershipRepository(
        private val record: OwnershipRecord?,
    ) : OwnershipRepository {
        override fun save(record: OwnershipRecord): OwnershipRecord = record

        override fun findById(id: UUID): OwnershipRecord? = record?.takeIf { it.id == id }

        override fun findActiveByOwnerId(ownerId: UUID): List<OwnershipRecord> = emptyList()

        override fun findActiveOverlapping(range: TimeRange): List<OwnershipRecord> = emptyList()
    }

    private class FakeMediaAssetRepository(
        private val assets: List<MediaAsset> = emptyList(),
    ) : MediaAssetRepository {
        override fun save(asset: MediaAsset): MediaAsset = asset

        override fun update(asset: MediaAsset): MediaAsset = asset

        override fun findById(id: UUID): MediaAsset? = assets.find { it.id == id }

        override fun findByIdForUpdate(id: UUID): MediaAsset? = assets.find { it.id == id }

        override fun findByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> =
            assets.filter { it.ownershipRecordId == ownershipRecordId }

        override fun findApprovedByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> = emptyList()

        override fun findByModerationStatus(status: ModerationStatus): List<MediaAsset> =
            assets.filter { it.moderationStatus == status }

        override fun findByOwnerId(ownerId: UUID): List<MediaAsset> = assets.filter { it.ownerId == ownerId }
    }
}
