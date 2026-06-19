package com.timearchive.application

import com.timearchive.domain.model.AuditLog
import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.port.AuditLogPort
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.TransactionPort
import java.util.UUID

class RejectMediaAsset(
    private val transactionPort: TransactionPort,
    private val mediaAssetRepository: MediaAssetRepository,
    private val auditLogPort: AuditLogPort,
    private val clockPort: ClockPort,
    private val idGenerator: () -> UUID = UUID::randomUUID,
) {
    fun reject(command: Command): MediaAsset =
        transactionPort.execute {
            val asset = mediaAssetRepository.findByIdForUpdate(command.mediaAssetId)
                ?: throw IllegalStateException("media asset not found")
            val now = clockPort.now()

            val rejected = mediaAssetRepository.update(asset.reject(now = now))
            auditLogPort.append(
                AuditLog(
                    id = idGenerator(),
                    actorUserId = command.adminId,
                    actorType = "USER",
                    action = "MEDIA_ASSET_REJECTED",
                    resourceType = "MEDIA_ASSET",
                    resourceId = rejected.id,
                    beforeState = asset.auditStateJson(),
                    afterState = rejected.auditStateJson(),
                    requestId = null,
                    createdAt = now,
                ),
            )
            rejected
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
