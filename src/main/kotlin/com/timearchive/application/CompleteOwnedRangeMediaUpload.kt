package com.timearchive.application

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaUploadRequest
import com.timearchive.domain.model.MediaUploadRequestStatus
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.MediaObjectStoragePort
import com.timearchive.domain.port.MediaUploadRequestRepository
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.TransactionPort
import java.util.UUID

class CompleteOwnedRangeMediaUpload(
    private val transactionPort: TransactionPort,
    private val ownershipRepository: OwnershipRepository,
    private val mediaUploadRequestRepository: MediaUploadRequestRepository,
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaObjectStoragePort: MediaObjectStoragePort,
    private val clockPort: ClockPort,
    private val idGenerator: () -> UUID = UUID::randomUUID,
) {
    fun complete(command: Command): Result =
        transactionPort.execute {
            val uploadRequest = mediaUploadRequestRepository.findByIdForUpdate(command.uploadRequestId)
                ?: throw IllegalStateException("media upload request not found")

            require(uploadRequest.ownershipRecordId == command.ownershipRecordId) {
                "media upload request does not belong to ownership record"
            }
            require(uploadRequest.ownerId == command.currentUserId) {
                "ownership record is not owned by current user"
            }

            val ownership = ownershipRepository.findById(command.ownershipRecordId)
                ?: throw IllegalStateException("ownership record not found")
            require(ownership.isActive) { "ownership record is not active" }
            require(ownership.ownerId == command.currentUserId) {
                "ownership record is not owned by current user"
            }

            if (uploadRequest.status == MediaUploadRequestStatus.COMPLETED) {
                val mediaAssetId = requireNotNull(uploadRequest.mediaAssetId) {
                    "completed upload request must have mediaAssetId"
                }
                return@execute Result(
                    uploadRequest = uploadRequest,
                    mediaAsset = mediaAssetRepository.findById(mediaAssetId)
                        ?: throw IllegalStateException("completed media asset not found"),
                    alreadyCompleted = true,
                )
            }

            require(uploadRequest.status == MediaUploadRequestStatus.REQUESTED) {
                "media upload request is not completable"
            }
            val now = clockPort.now()
            require(!now.isAfter(uploadRequest.expiresAt)) { "media upload request is expired" }

            val objectMetadata = mediaObjectStoragePort.findObjectMetadata(uploadRequest.objectKey)
                ?: throw IllegalStateException("uploaded media object not found")

            require(objectMetadata.contentLengthBytes == uploadRequest.contentLengthBytes) {
                "uploaded media content length does not match upload request"
            }
            require(objectMetadata.contentType == uploadRequest.contentType) {
                "uploaded media content type does not match upload request"
            }

            val mediaAsset = mediaAssetRepository.save(
                MediaAsset.uploaded(
                    id = idGenerator(),
                    ownershipRecordId = uploadRequest.ownershipRecordId,
                    ownerId = uploadRequest.ownerId,
                    mediaType = uploadRequest.mediaType,
                    originalFileUrl = uploadRequest.originalFileUrl,
                    now = now,
                ),
            )
            val completedUploadRequest = uploadRequest.complete(
                mediaAssetId = mediaAsset.id,
                now = now,
            )
            val updatedRows = mediaUploadRequestRepository.markCompleted(
                id = uploadRequest.id,
                mediaAssetId = mediaAsset.id,
                now = now,
            )
            check(updatedRows == 1) { "media upload request completion failed" }

            Result(
                uploadRequest = completedUploadRequest,
                mediaAsset = mediaAsset,
                alreadyCompleted = false,
            )
        }

    data class Command(
        val currentUserId: UUID,
        val ownershipRecordId: UUID,
        val uploadRequestId: UUID,
    )

    data class Result(
        val uploadRequest: MediaUploadRequest,
        val mediaAsset: MediaAsset,
        val alreadyCompleted: Boolean,
    )
}
