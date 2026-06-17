package com.timearchive.domain.port

import com.timearchive.domain.model.MediaUploadRequest
import java.util.UUID

interface MediaUploadRequestRepository {
    fun save(request: MediaUploadRequest): MediaUploadRequest

    fun findById(id: UUID): MediaUploadRequest?

    fun findByIdForUpdate(id: UUID): MediaUploadRequest?

    fun findByOwnershipRecordId(ownershipRecordId: UUID): List<MediaUploadRequest>

    fun markCompleted(
        id: UUID,
        mediaAssetId: UUID,
        now: java.time.Instant,
    ): Int
}
