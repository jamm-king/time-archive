package com.timearchive.application

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.ModerationStatus
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.TransactionPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CreateOwnedRangeMediaAssetTest {
    private val now: Instant = Instant.parse("2026-06-17T00:00:00Z")
    private val ownerId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val ownershipRecord: OwnershipRecord = activeOwnership(ownerId = ownerId)

    @Test
    fun `creates uploaded media asset for active owned range`() {
        val mediaRepository = FakeMediaAssetRepository()
        val useCase = useCase(
            ownershipRepository = FakeOwnershipRepository(record = ownershipRecord),
            mediaAssetRepository = mediaRepository,
        )

        val result = useCase.create(command(currentUserId = ownerId))

        assertThat(result.ownershipRecordId).isEqualTo(ownershipRecord.id)
        assertThat(result.ownerId).isEqualTo(ownerId)
        assertThat(result.mediaType).isEqualTo(MediaType.IMAGE)
        assertThat(result.originalFileUrl).isEqualTo("https://cdn.example.com/original.png")
        assertThat(result.moderationStatus.name).isEqualTo("UPLOADED")
        assertThat(mediaRepository.saved).containsExactly(result)
    }

    @Test
    fun `rejects media creation for missing ownership record`() {
        val useCase = useCase(ownershipRepository = FakeOwnershipRepository(record = null))

        assertThatIllegalStateException()
            .isThrownBy { useCase.create(command(currentUserId = ownerId)) }
            .withMessage("ownership record not found")
    }

    @Test
    fun `rejects media creation for another owner's range`() {
        val useCase = useCase(ownershipRepository = FakeOwnershipRepository(record = ownershipRecord))

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.create(command(currentUserId = UUID.randomUUID())) }
            .withMessage("ownership record is not owned by current user")
    }

    @Test
    fun `rejects media creation for inactive ownership record`() {
        val inactive = ownershipRecord.copy(
            status = com.timearchive.domain.model.OwnershipStatus.TRANSFERRED,
            validUntil = now.plusSeconds(1),
            updatedAt = now.plusSeconds(1),
        )
        val useCase = useCase(ownershipRepository = FakeOwnershipRepository(record = inactive))

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.create(command(currentUserId = ownerId)) }
            .withMessage("ownership record is not active")
    }

    private fun useCase(
        ownershipRepository: OwnershipRepository,
        mediaAssetRepository: MediaAssetRepository = FakeMediaAssetRepository(),
    ): CreateOwnedRangeMediaAsset =
        CreateOwnedRangeMediaAsset(
            transactionPort = ImmediateTransactionPort,
            ownershipRepository = ownershipRepository,
            mediaAssetRepository = mediaAssetRepository,
            clockPort = ClockPort { now },
            idGenerator = { UUID.fromString("00000000-0000-0000-0000-000000000201") },
        )

    private fun command(currentUserId: UUID): CreateOwnedRangeMediaAsset.Command =
        CreateOwnedRangeMediaAsset.Command(
            currentUserId = currentUserId,
            ownershipRecordId = ownershipRecord.id,
            mediaType = MediaType.IMAGE,
            originalFileUrl = "https://cdn.example.com/original.png",
            thumbnailUrl = "https://cdn.example.com/thumb.png",
            externalLink = "https://example.com",
        )

    private fun activeOwnership(ownerId: UUID): OwnershipRecord =
        OwnershipRecord.active(
            id = UUID.fromString("00000000-0000-0000-0000-000000000301"),
            range = TimeRange(startSecond = 10, endSecond = 11),
            ownerId = ownerId,
            validFrom = now,
            acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
        )

    private object ImmediateTransactionPort : TransactionPort {
        override fun <T> execute(block: () -> T): T = block()
    }

    private class FakeOwnershipRepository(
        private val record: OwnershipRecord?,
    ) : OwnershipRepository {
        override fun save(record: OwnershipRecord): OwnershipRecord = record

        override fun findById(id: UUID): OwnershipRecord? = record?.takeIf { it.id == id }

        override fun findActiveOverlapping(range: TimeRange): List<OwnershipRecord> = emptyList()
    }

    private class FakeMediaAssetRepository : MediaAssetRepository {
        val saved = mutableListOf<MediaAsset>()

        override fun save(asset: MediaAsset): MediaAsset {
            saved.add(asset)
            return asset
        }

        override fun update(asset: MediaAsset): MediaAsset = asset

        override fun findById(id: UUID): MediaAsset? = saved.find { it.id == id }

        override fun findByIdForUpdate(id: UUID): MediaAsset? = saved.find { it.id == id }

        override fun findByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> =
            saved.filter { it.ownershipRecordId == ownershipRecordId }

        override fun findApprovedByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> = emptyList()

        override fun findByModerationStatus(status: ModerationStatus): List<MediaAsset> =
            saved.filter { it.moderationStatus == status }

        override fun findByOwnerId(ownerId: UUID): List<MediaAsset> = saved.filter { it.ownerId == ownerId }
    }
}
