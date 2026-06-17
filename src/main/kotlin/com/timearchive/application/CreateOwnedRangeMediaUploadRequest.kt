package com.timearchive.application

import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.MediaUploadRequest
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaObjectStoragePort
import com.timearchive.domain.port.MediaUploadRequestRepository
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.TransactionPort
import java.time.Duration
import java.util.UUID

class CreateOwnedRangeMediaUploadRequest(
    private val transactionPort: TransactionPort,
    private val ownershipRepository: OwnershipRepository,
    private val mediaUploadRequestRepository: MediaUploadRequestRepository,
    private val mediaObjectStoragePort: MediaObjectStoragePort,
    private val clockPort: ClockPort,
    private val uploadUrlTtl: Duration = Duration.ofMinutes(10),
    private val idGenerator: () -> UUID = UUID::randomUUID,
) {
    fun create(command: Command): Result =
        transactionPort.execute {
            val ownership = ownershipRepository.findById(command.ownershipRecordId)
                ?: throw IllegalStateException("ownership record not found")

            require(ownership.isActive) { "ownership record is not active" }
            require(ownership.ownerId == command.currentUserId) {
                "ownership record is not owned by current user"
            }

            validateMediaPolicy(command)

            val now = clockPort.now()
            val expiresAt = now.plus(uploadUrlTtl)
            val uploadRequestId = idGenerator()
            val objectKey = buildObjectKey(
                ownerId = ownership.ownerId,
                ownershipRecordId = ownership.id,
                uploadRequestId = uploadRequestId,
                originalFilename = command.originalFilename,
            )

            val presignedUpload = mediaObjectStoragePort.createPresignedUpload(
                MediaObjectStoragePort.Command(
                    objectKey = objectKey,
                    contentType = command.contentType,
                    contentLengthBytes = command.contentLengthBytes,
                    expiresAt = expiresAt,
                ),
            )

            val uploadRequest = mediaUploadRequestRepository.save(
                MediaUploadRequest.requested(
                    id = uploadRequestId,
                    ownershipRecordId = ownership.id,
                    ownerId = ownership.ownerId,
                    mediaType = command.mediaType,
                    originalFilename = command.originalFilename,
                    contentType = command.contentType,
                    contentLengthBytes = command.contentLengthBytes,
                    objectKey = objectKey,
                    originalFileUrl = presignedUpload.originalFileUrl,
                    now = now,
                    expiresAt = expiresAt,
                ),
            )

            Result(
                uploadRequest = uploadRequest,
                uploadUrl = presignedUpload.uploadUrl,
                requiredHeaders = presignedUpload.requiredHeaders,
            )
        }

    private fun validateMediaPolicy(command: Command) {
        require(command.originalFilename.isNotBlank()) { "originalFilename must not be blank" }
        require(command.contentLengthBytes > 0) { "contentLengthBytes must be greater than 0" }

        val allowedContentTypes = when (command.mediaType) {
            MediaType.IMAGE -> setOf("image/jpeg", "image/png", "image/webp")
            MediaType.VIDEO -> setOf("video/mp4")
        }
        require(command.contentType in allowedContentTypes) {
            "contentType is not allowed for mediaType"
        }

        val maxBytes = when (command.mediaType) {
            MediaType.IMAGE -> IMAGE_MAX_BYTES
            MediaType.VIDEO -> VIDEO_MAX_BYTES
        }
        require(command.contentLengthBytes <= maxBytes) {
            "contentLengthBytes exceeds max allowed size"
        }
    }

    private fun buildObjectKey(
        ownerId: UUID,
        ownershipRecordId: UUID,
        uploadRequestId: UUID,
        originalFilename: String,
    ): String {
        val safeFilename = originalFilename
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-', '.', '_')
            .ifBlank { "upload" }

        return "media/originals/$ownerId/$ownershipRecordId/$uploadRequestId/$safeFilename"
    }

    data class Command(
        val currentUserId: UUID,
        val ownershipRecordId: UUID,
        val mediaType: MediaType,
        val originalFilename: String,
        val contentType: String,
        val contentLengthBytes: Long,
    )

    data class Result(
        val uploadRequest: MediaUploadRequest,
        val uploadUrl: String,
        val requiredHeaders: Map<String, String>,
    )

    companion object {
        const val IMAGE_MAX_BYTES: Long = 10L * 1024L * 1024L
        const val VIDEO_MAX_BYTES: Long = 100L * 1024L * 1024L
    }
}
