package com.timearchive.application

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.MediaUploadRequest
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaObjectStoragePort
import com.timearchive.domain.port.MediaUploadRequestRepository
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.TransactionPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CreateOwnedRangeMediaUploadRequestTest {
    private val now: Instant = Instant.parse("2026-06-17T00:00:00Z")
    private val ownerId: UUID = UUID.fromString("00000000-0000-0000-0000-000000001001")
    private val ownershipRecord: OwnershipRecord = OwnershipRecord.active(
        id = UUID.fromString("00000000-0000-0000-0000-000000001002"),
        range = TimeRange(startSecond = 10, endSecond = 11),
        ownerId = ownerId,
        validFrom = now,
        acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
    )

    @Test
    fun `creates upload request for active owned range`() {
        val repository = FakeMediaUploadRequestRepository()
        val storage = FakeMediaObjectStoragePort()
        val useCase = useCase(repository = repository, storage = storage)

        val result = useCase.create(command(currentUserId = ownerId))

        assertThat(result.uploadRequest.ownerId).isEqualTo(ownerId)
        assertThat(result.uploadRequest.ownershipRecordId).isEqualTo(ownershipRecord.id)
        assertThat(result.uploadRequest.mediaType).isEqualTo(MediaType.IMAGE)
        assertThat(result.uploadRequest.contentType).isEqualTo("image/png")
        assertThat(result.uploadRequest.contentLengthBytes).isEqualTo(1024)
        assertThat(result.uploadRequest.expiresAt).isEqualTo(now.plusSeconds(600))
        assertThat(result.uploadRequest.objectKey)
            .isEqualTo("media/originals/$ownerId/${ownershipRecord.id}/00000000-0000-0000-0000-000000001003/original.png")
        assertThat(result.uploadUrl).contains(result.uploadRequest.objectKey)
        assertThat(repository.saved).containsExactly(result.uploadRequest)
        assertThat(storage.commands).containsExactly(
            MediaObjectStoragePort.Command(
                objectKey = result.uploadRequest.objectKey,
                contentType = "image/png",
                contentLengthBytes = 1024,
                expiresAt = now.plusSeconds(600),
            ),
        )
    }

    @Test
    fun `rejects upload request for another owner's range`() {
        val useCase = useCase()

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.create(command(currentUserId = UUID.randomUUID())) }
            .withMessage("ownership record is not owned by current user")
    }

    @Test
    fun `rejects unsupported content type`() {
        val useCase = useCase()

        assertThatIllegalArgumentException()
            .isThrownBy {
                useCase.create(
                    command(
                        currentUserId = ownerId,
                        contentType = "image/svg+xml",
                    ),
                )
            }
            .withMessage("contentType is not allowed for mediaType")
    }

    @Test
    fun `rejects content length above media policy`() {
        val useCase = useCase()

        assertThatIllegalArgumentException()
            .isThrownBy {
                useCase.create(
                    command(
                        currentUserId = ownerId,
                        contentLengthBytes = CreateOwnedRangeMediaUploadRequest.IMAGE_MAX_BYTES + 1,
                    ),
                )
            }
            .withMessage("contentLengthBytes exceeds max allowed size")
    }

    private fun useCase(
        repository: MediaUploadRequestRepository = FakeMediaUploadRequestRepository(),
        storage: MediaObjectStoragePort = FakeMediaObjectStoragePort(),
    ): CreateOwnedRangeMediaUploadRequest =
        CreateOwnedRangeMediaUploadRequest(
            transactionPort = ImmediateTransactionPort,
            ownershipRepository = FakeOwnershipRepository(ownershipRecord),
            mediaUploadRequestRepository = repository,
            mediaObjectStoragePort = storage,
            clockPort = ClockPort { now },
            uploadUrlTtl = Duration.ofMinutes(10),
            idGenerator = { UUID.fromString("00000000-0000-0000-0000-000000001003") },
        )

    private fun command(
        currentUserId: UUID,
        contentType: String = "image/png",
        contentLengthBytes: Long = 1024,
    ): CreateOwnedRangeMediaUploadRequest.Command =
        CreateOwnedRangeMediaUploadRequest.Command(
            currentUserId = currentUserId,
            ownershipRecordId = ownershipRecord.id,
            mediaType = MediaType.IMAGE,
            originalFilename = "Original.png",
            contentType = contentType,
            contentLengthBytes = contentLengthBytes,
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

    private class FakeMediaUploadRequestRepository : MediaUploadRequestRepository {
        val saved = mutableListOf<MediaUploadRequest>()

        override fun save(request: MediaUploadRequest): MediaUploadRequest {
            saved.add(request)
            return request
        }

        override fun findById(id: UUID): MediaUploadRequest? = saved.find { it.id == id }

        override fun findByIdForUpdate(id: UUID): MediaUploadRequest? = saved.find { it.id == id }

        override fun findByOwnershipRecordId(ownershipRecordId: UUID): List<MediaUploadRequest> =
            saved.filter { it.ownershipRecordId == ownershipRecordId }

        override fun markCompleted(
            id: UUID,
            mediaAssetId: UUID,
            now: Instant,
        ): Int = 0
    }

    private class FakeMediaObjectStoragePort : MediaObjectStoragePort {
        val commands = mutableListOf<MediaObjectStoragePort.Command>()

        override fun createPresignedUpload(
            command: MediaObjectStoragePort.Command,
        ): MediaObjectStoragePort.PresignedUpload {
            commands.add(command)
            return MediaObjectStoragePort.PresignedUpload(
                uploadUrl = "http://localhost:9000/time-archive-media/${command.objectKey}?signature=test",
                originalFileUrl = "http://localhost:9000/time-archive-media/${command.objectKey}",
                requiredHeaders = mapOf("content-type" to command.contentType),
            )
        }

        override fun createPresignedDownload(
            command: MediaObjectStoragePort.DownloadCommand,
        ): MediaObjectStoragePort.PresignedDownload =
            error("not used")

        override fun findObjectMetadata(objectKey: String): MediaObjectStoragePort.ObjectMetadata? = null
    }
}
