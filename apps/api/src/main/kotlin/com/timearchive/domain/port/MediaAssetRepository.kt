package com.timearchive.domain.port

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.ModerationStatus
import java.util.UUID

interface MediaAssetRepository {
    fun save(asset: MediaAsset): MediaAsset

    fun update(asset: MediaAsset): MediaAsset

    fun findById(id: UUID): MediaAsset?

    fun findByIdForUpdate(id: UUID): MediaAsset?

    fun findByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset>

    fun findApprovedByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset>

    fun findByModerationStatus(status: ModerationStatus): List<MediaAsset>

    fun findByOwnerId(ownerId: UUID): List<MediaAsset>
}
