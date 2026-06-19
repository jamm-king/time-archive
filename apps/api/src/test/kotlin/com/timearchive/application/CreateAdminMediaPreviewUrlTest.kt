package com.timearchive.application

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.ModerationStatus
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.MediaObjectStoragePort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CreateAdminMediaPreviewUrlTest {
    private val now: Instant = Instant.parse("2026-06-19T00:00:00Z")
    private val adminId: UUID = UUID.fromString("00000000-0000-0000-0000-000000005001")
    private val mediaAssetId: UUID = UUID.fromString("00000000-0000-0000-0000-000000005002")

    @Test
    fun `creates presigned preview url for original media`() {
        val mediaAsset = uploadedMediaAsset()
        val storage = FakeMediaObjectStoragePort()
        val useCase = useCase(
            repository = FakeMediaAssetRepository(assets = listOf(mediaAsset)),
            storage = storage,
        )

        val result = useCase.create(
            CreateAdminMediaPreviewUrl.Command(
                adminId = adminId,
                mediaAssetId = mediaAssetId,
            ),
        )

        assertThat(result.mediaAssetId).isEqualTo(mediaAssetId)
        assertThat(result.previewUrl).isEqualTo("https://storage.example.test/presigned-original.png")
        assertThat(result.expiresAt).isEqualTo(now.plusSeconds(300))
        assertThat(storage.downloadCommands).containsExactly(
            MediaObjectStoragePort.DownloadCommand(
                fileUrl = mediaAsset.originalFileUrl,
                expiresAt = now.plusSeconds(300),
            ),
        )
    }

    @Test
    fun `rejects preview url for missing media asset`() {
        val useCase = useCase(repository = FakeMediaAssetRepository(assets = emptyList()))

        assertThatIllegalStateException()
            .isThrownBy {
                useCase.create(
                    CreateAdminMediaPreviewUrl.Command(
                        adminId = adminId,
                        mediaAssetId = mediaAssetId,
                    ),
                )
            }
            .withMessage("media asset not found")
    }

    private fun useCase(
        repository: MediaAssetRepository = FakeMediaAssetRepository(assets = listOf(uploadedMediaAsset())),
        storage: MediaObjectStoragePort = FakeMediaObjectStoragePort(),
    ): CreateAdminMediaPreviewUrl =
        CreateAdminMediaPreviewUrl(
            mediaAssetRepository = repository,
            mediaObjectStoragePort = storage,
            clockPort = ClockPort { now },
            previewUrlTtl = Duration.ofSeconds(300),
        )

    private fun uploadedMediaAsset(): MediaAsset =
        MediaAsset.uploaded(
            id = mediaAssetId,
            ownershipRecordId = UUID.fromString("00000000-0000-0000-0000-000000005003"),
            ownerId = UUID.fromString("00000000-0000-0000-0000-000000005004"),
            mediaType = MediaType.IMAGE,
            originalFileUrl = "https://storage.example.test/original.png",
            now = now,
        )

    private class FakeMediaAssetRepository(
        private val assets: List<MediaAsset>,
    ) : MediaAssetRepository {
        override fun save(asset: MediaAsset): MediaAsset = asset

        override fun update(asset: MediaAsset): MediaAsset = asset

        override fun findById(id: UUID): MediaAsset? = assets.find { it.id == id }

        override fun findByIdForUpdate(id: UUID): MediaAsset? = findById(id)

        override fun findByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> =
            assets.filter { it.ownershipRecordId == ownershipRecordId }

        override fun findApprovedByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> =
            assets.filter { it.ownershipRecordId == ownershipRecordId && it.moderationStatus == ModerationStatus.APPROVED }

        override fun findByModerationStatus(status: ModerationStatus): List<MediaAsset> =
            assets.filter { it.moderationStatus == status }

        override fun findByOwnerId(ownerId: UUID): List<MediaAsset> =
            assets.filter { it.ownerId == ownerId }
    }

    private class FakeMediaObjectStoragePort : MediaObjectStoragePort {
        val downloadCommands = mutableListOf<MediaObjectStoragePort.DownloadCommand>()

        override fun createPresignedUpload(command: MediaObjectStoragePort.Command): MediaObjectStoragePort.PresignedUpload =
            error("not used")

        override fun createPresignedDownload(
            command: MediaObjectStoragePort.DownloadCommand,
        ): MediaObjectStoragePort.PresignedDownload {
            downloadCommands.add(command)
            return MediaObjectStoragePort.PresignedDownload(
                downloadUrl = "https://storage.example.test/presigned-original.png",
                expiresAt = command.expiresAt,
            )
        }

        override fun isManagedFileUrl(fileUrl: String): Boolean = true

        override fun findObjectMetadata(objectKey: String): MediaObjectStoragePort.ObjectMetadata? = null
    }
}
