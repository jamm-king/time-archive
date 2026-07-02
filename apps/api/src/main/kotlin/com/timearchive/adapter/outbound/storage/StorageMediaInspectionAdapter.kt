package com.timearchive.adapter.outbound.storage

import com.timearchive.domain.port.MediaInspectionPort
import com.timearchive.domain.port.MediaObjectStoragePort
import org.springframework.stereotype.Component

@Component
class StorageMediaInspectionAdapter(
    private val mediaObjectStoragePort: MediaObjectStoragePort,
    private val mp4DurationParser: Mp4DurationParser = Mp4DurationParser(),
    private val signatureDetector: MediaFileSignatureDetector = MediaFileSignatureDetector(),
) : MediaInspectionPort {
    override fun inspect(command: MediaInspectionPort.Command): MediaInspectionPort.Result {
        val signatureMatches = mediaObjectStoragePort.openObject(command.objectKey)?.use { input ->
            signatureDetector.matchesContentType(
                input = input,
                contentType = command.contentType,
            )
        } ?: throw IllegalStateException("uploaded media object not found")

        if (command.contentType != "video/mp4") {
            return MediaInspectionPort.Result(
                signatureMatchesContentType = signatureMatches,
                durationMs = null,
            )
        }

        val durationMs = mediaObjectStoragePort.openObject(command.objectKey)?.use { input ->
            runCatching { mp4DurationParser.parseDurationMs(input) }
                .getOrElse { throw IllegalArgumentException("uploaded video duration metadata not found", it) }
        } ?: throw IllegalStateException("uploaded media object not found")

        return MediaInspectionPort.Result(
            signatureMatchesContentType = signatureMatches,
            durationMs = durationMs,
        )
    }
}
