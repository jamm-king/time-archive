package com.timearchive.application

import com.timearchive.domain.model.AuditLog
import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.port.AuditLogPort
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.TransactionPort
import java.util.UUID

class HideMediaAsset(
    private val transactionPort: TransactionPort,
    private val mediaAssetRepository: MediaAssetRepository,
    private val auditLogPort: AuditLogPort,
    private val clockPort: ClockPort,
    private val idGenerator: () -> UUID = UUID::randomUUID,
) {
    fun hide(command: Command): MediaAsset =
        transactionPort.execute {
            val asset = mediaAssetRepository.findByIdForUpdate(command.mediaAssetId)
                ?: throw IllegalStateException("media asset not found")
            val now = clockPort.now()

            val hidden = mediaAssetRepository.update(asset.hide(now = now))
            auditLogPort.append(
                AuditLog(
                    id = idGenerator(),
                    actorUserId = command.adminId,
                    actorType = "USER",
                    action = "MEDIA_ASSET_HIDDEN",
                    resourceType = "MEDIA_ASSET",
                    resourceId = hidden.id,
                    beforeState = asset.auditStateJson(),
                    afterState = hidden.auditStateJson(),
                    requestId = null,
                    createdAt = now,
                ),
            )
            hidden
        }

    data class Command(
        val adminId: UUID,
        val mediaAssetId: UUID,
    )
}

private fun MediaAsset.auditStateJson(): String =
    """{"moderationStatus":"${moderationStatus.name}","approvedFileUrl":${approvedFileUrl.auditJsonValue()},"thumbnailUrl":${thumbnailUrl.auditJsonValue()}}"""

private fun String?.auditJsonValue(): String =
    this?.let { """"${it.replace("\\", "\\\\").replace("\"", "\\\"")}"""" } ?: "null"
