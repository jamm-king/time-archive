package com.timearchive.adapter.inbound.rest

data class ApiErrorResponse(
    val code: String,
    val message: String,
    val details: List<ApiErrorDetail> = emptyList(),
    val requestId: String? = null,
)

data class ApiErrorDetail(
    val field: String?,
    val message: String,
)
