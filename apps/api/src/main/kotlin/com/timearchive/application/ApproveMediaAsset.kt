package com.timearchive.application

import com.timearchive.domain.model.AuditLog
import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.port.AuditLogPort
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.MediaObjectStoragePort
import com.timearchive.domain.port.TransactionPort
import java.util.UUID

class ApproveMediaAsset(
    private val transactionPort: TransactionPort,
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaObjectStoragePort: MediaObjectStoragePort,
    private val auditLogPort: AuditLogPort,
    private val clockPort: ClockPort,
    private val idGenerator: () -> UUID = UUID::randomUUID,
) {
    fun approve(command: Command): MediaAsset =
        transactionPort.execute {
            val asset = mediaAssetRepository.findByIdForUpdate(command.mediaAssetId)
                ?: throw IllegalStateException("media asset not found")
            val now = clockPort.now()

            validateStorageReferences(command)

            val approved = mediaAssetRepository.update(
                asset.approve(
                    approvedFileUrl = command.approvedFileUrl,
                    thumbnailUrl = command.thumbnailUrl,
                    now = now,
                ),
            )
            auditLogPort.append(
                AuditLog(
                    id = idGenerator(),
                    actorUserId = command.adminId,
                    actorType = "USER",
                    action = "MEDIA_ASSET_APPROVED",
                    resourceType = "MEDIA_ASSET",
                    resourceId = approved.id,
                    beforeState = asset.auditStateJson(),
                    afterState = approved.auditStateJson(),
                    requestId = null,
                    createdAt = now,
                ),
            )
            approved
        }

    data class Command(
        val adminId: UUID,
        val mediaAssetId: UUID,
        val approvedFileUrl: String,
        val thumbnailUrl: String?,
    )

    private fun validateStorageReferences(command: Command) {
        require(mediaObjectStoragePort.isManagedFileUrl(command.approvedFileUrl)) {
            "approved media file url is not managed by storage"
        }
        require(command.thumbnailUrl == null || mediaObjectStoragePort.isManagedFileUrl(command.thumbnailUrl)) {
            "approved media thumbnail url is not managed by storage"
        }
    }
}

private fun MediaAsset.auditStateJson(): String =
    """{"moderationStatus":"${moderationStatus.name}","approvedFileUrl":${approvedFileUrl.auditJsonValue()},"thumbnailUrl":${thumbnailUrl.auditJsonValue()}}"""

private fun String?.auditJsonValue(): String =
    this?.let { """"${it.replace("\\", "\\\\").replace("\"", "\\\"")}"""" } ?: "null"
