package com.timearchive.application

import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.MediaObjectStoragePort
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CreateAdminMediaPreviewUrl(
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaObjectStoragePort: MediaObjectStoragePort,
    private val clockPort: ClockPort,
    private val previewUrlTtl: Duration,
) {
    fun create(command: Command): Result {
        val mediaAsset = mediaAssetRepository.findById(command.mediaAssetId)
            ?: error("media asset not found")
        val expiresAt = clockPort.now().plus(previewUrlTtl)
        val download = mediaObjectStoragePort.createPresignedDownload(
            MediaObjectStoragePort.DownloadCommand(
                fileUrl = mediaAsset.originalFileUrl,
                expiresAt = expiresAt,
            ),
        )

        return Result(
            mediaAssetId = mediaAsset.id,
            previewUrl = download.downloadUrl,
            expiresAt = download.expiresAt,
        )
    }

    data class Command(
        val adminId: UUID,
        val mediaAssetId: UUID,
    )

    data class Result(
        val mediaAssetId: UUID,
        val previewUrl: String,
        val expiresAt: Instant,
    )
}
