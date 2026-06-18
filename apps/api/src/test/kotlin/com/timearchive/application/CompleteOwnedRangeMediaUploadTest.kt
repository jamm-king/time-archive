package com.timearchive.application

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.MediaUploadRequest
import com.timearchive.domain.model.ModerationStatus
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.MediaObjectStoragePort
import com.timearchive.domain.port.MediaUploadRequestRepository
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.TransactionPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CompleteOwnedRangeMediaUploadTest {
    private val now: Instant = Instant.parse("2026-06-17T00:00:00Z")
    private val ownerId: UUID = UUID.fromString("00000000-0000-0000-0000-000000002001")
    private val ownershipRecord: OwnershipRecord = OwnershipRecord.active(
        id = UUID.fromString("00000000-0000-0000-0000-000000002002"),
        range = TimeRange(startSecond = 10, endSecond = 11),
        ownerId = ownerId,
        validFrom = now.minusSeconds(60),
        acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
    )
    private val uploadRequestId: UUID = UUID.fromString("00000000-0000-0000-0000-000000002003")
    private val mediaAssetId: UUID = UUID.fromString("00000000-0000-0000-0000-000000002004")

    @Test
    fun `completes upload request and creates uploaded media asset`() {
        val uploadRequestRepository = FakeMediaUploadRequestRepository(uploadRequest())
        val mediaAssetRepository = FakeMediaAssetRepository()
        val useCase = useCase(
            uploadRequestRepository = uploadRequestRepository,
            mediaAssetRepository = mediaAssetRepository,
        )

        val result = useCase.complete(command())

        assertThat(result.alreadyCompleted).isFalse()
        assertThat(result.mediaAsset.id).isEqualTo(mediaAssetId)
        assertThat(result.mediaAsset.ownershipRecordId).isEqualTo(ownershipRecord.id)
        assertThat(result.mediaAsset.originalFileUrl).isEqualTo("http://localhost:9000/time-archive-media/object")
        assertThat(result.mediaAsset.moderationStatus.name).isEqualTo("UPLOADED")
        assertThat(result.uploadRequest.status.name).isEqualTo("COMPLETED")
        assertThat(result.uploadRequest.mediaAssetId).isEqualTo(mediaAssetId)
        assertThat(mediaAssetRepository.saved).containsExactly(result.mediaAsset)
        assertThat(uploadRequestRepository.completedMediaAssetIds).containsExactly(mediaAssetId)
    }

    @Test
    fun `returns existing media asset when upload request is already completed`() {
        val mediaAsset = MediaAsset.uploaded(
            id = mediaAssetId,
            ownershipRecordId = ownershipRecord.id,
            ownerId = ownerId,
            mediaType = MediaType.IMAGE,
            originalFileUrl = "http://localhost:9000/time-archive-media/object",
            now = now,
        )
        val useCase = useCase(
            uploadRequestRepository = FakeMediaUploadRequestRepository(
                uploadRequest().complete(mediaAssetId = mediaAssetId, now = now),
            ),
            mediaAssetRepository = FakeMediaAssetRepository(existing = mediaAsset),
        )

        val result = useCase.complete(command())

        assertThat(result.alreadyCompleted).isTrue()
        assertThat(result.mediaAsset).isEqualTo(mediaAsset)
    }

    @Test
    fun `rejects expired upload request`() {
        val useCase = useCase(
            uploadRequestRepository = FakeMediaUploadRequestRepository(
                uploadRequest(expiresAt = now.minusSeconds(1)),
            ),
        )

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.complete(command()) }
            .withMessage("media upload request is expired")
    }

    @Test
    fun `rejects missing uploaded object`() {
        val useCase = useCase(storage = FakeMediaObjectStoragePort(metadata = null))

        assertThatIllegalStateException()
            .isThrownBy { useCase.complete(command()) }
            .withMessage("uploaded media object not found")
    }

    @Test
    fun `rejects content length mismatch`() {
        val useCase = useCase(
            storage = FakeMediaObjectStoragePort(
                metadata = MediaObjectStoragePort.ObjectMetadata(
                    objectKey = "media/originals/object",
                    contentType = "image/png",
                    contentLengthBytes = 2048,
                ),
            ),
        )

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.complete(command()) }
            .withMessage("uploaded media content length does not match upload request")
    }

    private fun useCase(
        uploadRequestRepository: MediaUploadRequestRepository = FakeMediaUploadRequestRepository(uploadRequest()),
        mediaAssetRepository: MediaAssetRepository = FakeMediaAssetRepository(),
        storage: MediaObjectStoragePort = FakeMediaObjectStoragePort(),
    ): CompleteOwnedRangeMediaUpload =
        CompleteOwnedRangeMediaUpload(
            transactionPort = ImmediateTransactionPort,
            ownershipRepository = FakeOwnershipRepository(ownershipRecord),
            mediaUploadRequestRepository = uploadRequestRepository,
            mediaAssetRepository = mediaAssetRepository,
            mediaObjectStoragePort = storage,
            clockPort = ClockPort { now },
            idGenerator = { mediaAssetId },
        )

    private fun command(): CompleteOwnedRangeMediaUpload.Command =
        CompleteOwnedRangeMediaUpload.Command(
            currentUserId = ownerId,
            ownershipRecordId = ownershipRecord.id,
            uploadRequestId = uploadRequestId,
        )

    private fun uploadRequest(expiresAt: Instant = now.plusSeconds(600)): MediaUploadRequest =
        MediaUploadRequest.requested(
            id = uploadRequestId,
            ownershipRecordId = ownershipRecord.id,
            ownerId = ownerId,
            mediaType = MediaType.IMAGE,
            originalFilename = "original.png",
            contentType = "image/png",
            contentLengthBytes = 1024,
            objectKey = "media/originals/object",
            originalFileUrl = "http://localhost:9000/time-archive-media/object",
            now = now.minusSeconds(60),
            expiresAt = expiresAt,
        )

    private object ImmediateTransactionPort : TransactionPort {
        override fun <T> execute(block: () -> T): T = block()
    }

    private class FakeOwnershipRepository(
        private val record: OwnershipRecord,
    ) : OwnershipRepository {
        override fun save(record: OwnershipRecord): OwnershipRecord = record

        override fun findById(id: UUID): OwnershipRecord? = record.takeIf { it.id == id }

        override fun findActiveByOwnerId(ownerId: UUID): List<OwnershipRecord> = emptyList()

        override fun findActiveOverlapping(range: TimeRange): List<OwnershipRecord> = emptyList()
    }

    private class FakeMediaUploadRequestRepository(
        private val uploadRequest: MediaUploadRequest,
    ) : MediaUploadRequestRepository {
        val completedMediaAssetIds = mutableListOf<UUID>()

        override fun save(request: MediaUploadRequest): MediaUploadRequest = request

        override fun findById(id: UUID): MediaUploadRequest? = uploadRequest.takeIf { it.id == id }

        override fun findByIdForUpdate(id: UUID): MediaUploadRequest? = uploadRequest.takeIf { it.id == id }

        override fun findByOwnershipRecordId(ownershipRecordId: UUID): List<MediaUploadRequest> =
            listOf(uploadRequest).filter { it.ownershipRecordId == ownershipRecordId }

        override fun markCompleted(
            id: UUID,
            mediaAssetId: UUID,
            now: Instant,
        ): Int {
            completedMediaAssetIds.add(mediaAssetId)
            return 1
        }
    }

    private class FakeMediaAssetRepository(
        private val existing: MediaAsset? = null,
    ) : MediaAssetRepository {
        val saved = mutableListOf<MediaAsset>()

        override fun save(asset: MediaAsset): MediaAsset {
            saved.add(asset)
            return asset
        }

        override fun update(asset: MediaAsset): MediaAsset = asset

        override fun findById(id: UUID): MediaAsset? = existing?.takeIf { it.id == id }

        override fun findByIdForUpdate(id: UUID): MediaAsset? = existing?.takeIf { it.id == id }

        override fun findByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> = emptyList()

        override fun findApprovedByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> = emptyList()

        override fun findByModerationStatus(status: ModerationStatus): List<MediaAsset> = emptyList()

        override fun findByOwnerId(ownerId: UUID): List<MediaAsset> = emptyList()
    }

    private class FakeMediaObjectStoragePort(
        private val metadata: MediaObjectStoragePort.ObjectMetadata? = MediaObjectStoragePort.ObjectMetadata(
            objectKey = "media/originals/object",
            contentType = "image/png",
            contentLengthBytes = 1024,
        ),
    ) : MediaObjectStoragePort {
        override fun createPresignedUpload(command: MediaObjectStoragePort.Command): MediaObjectStoragePort.PresignedUpload =
            throw UnsupportedOperationException()

        override fun findObjectMetadata(objectKey: String): MediaObjectStoragePort.ObjectMetadata? = metadata
    }
}
