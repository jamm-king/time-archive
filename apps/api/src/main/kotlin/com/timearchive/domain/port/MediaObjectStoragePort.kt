package com.timearchive.domain.port

import java.time.Instant

interface MediaObjectStoragePort {
    fun createPresignedUpload(command: Command): PresignedUpload

    fun createPresignedDownload(command: DownloadCommand): PresignedDownload

    fun isManagedFileUrl(fileUrl: String): Boolean

    fun findObjectMetadata(objectKey: String): ObjectMetadata?

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

    data class DownloadCommand(
        val fileUrl: String,
        val expiresAt: Instant,
    )

    data class PresignedDownload(
        val downloadUrl: String,
        val expiresAt: Instant,
    )

    data class ObjectMetadata(
        val objectKey: String,
        val contentType: String?,
        val contentLengthBytes: Long,
    )
}
