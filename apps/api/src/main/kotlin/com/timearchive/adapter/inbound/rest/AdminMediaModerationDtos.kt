package com.timearchive.adapter.inbound.rest

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class ApproveMediaAssetRequest(
    @field:NotBlank
    val approvedFileUrl: String?,
    val thumbnailUrl: String?,
)

data class AdminMediaPreviewUrlResponse(
    val mediaAssetId: UUID,
    val previewUrl: String,
    val expiresAt: Instant,
)
