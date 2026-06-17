package com.timearchive.application

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.TransactionPort
import java.util.UUID

class HideMediaAsset(
    private val transactionPort: TransactionPort,
    private val mediaAssetRepository: MediaAssetRepository,
    private val clockPort: ClockPort,
) {
    fun hide(command: Command): MediaAsset =
        transactionPort.execute {
            val asset = mediaAssetRepository.findByIdForUpdate(command.mediaAssetId)
                ?: throw IllegalStateException("media asset not found")

            mediaAssetRepository.update(asset.hide(now = clockPort.now()))
        }

    data class Command(
        val adminId: UUID,
        val mediaAssetId: UUID,
    )
}
