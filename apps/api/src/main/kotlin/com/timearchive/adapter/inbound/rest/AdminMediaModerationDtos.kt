package com.timearchive.adapter.inbound.rest

import jakarta.validation.constraints.NotBlank

data class ApproveMediaAssetRequest(
    @field:NotBlank
    val approvedFileUrl: String?,
    val thumbnailUrl: String?,
)
