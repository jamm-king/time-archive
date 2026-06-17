package com.timearchive.application

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.ModerationStatus
import com.timearchive.domain.port.MediaAssetRepository

class ListMediaModerationQueue(
    private val mediaAssetRepository: MediaAssetRepository,
) {
    fun list(query: Query): List<MediaAsset> =
        mediaAssetRepository.findByModerationStatus(query.status)

    data class Query(
        val status: ModerationStatus = ModerationStatus.UPLOADED,
    )
}
