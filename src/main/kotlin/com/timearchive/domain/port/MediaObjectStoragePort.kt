package com.timearchive.domain.port

import java.time.Instant

interface MediaObjectStoragePort {
    fun createPresignedUpload(command: Command): PresignedUpload

    data class Command(
        val objectKey: String,
        val contentType: String,
        val contentLengthBytes: Long,
        val expiresAt: Instant,
    )

    data class PresignedUpload(
        val uploadUrl: String,
        val originalFileUrl: String,
        val requiredHeaders: Map<String, String>,
    )
}
