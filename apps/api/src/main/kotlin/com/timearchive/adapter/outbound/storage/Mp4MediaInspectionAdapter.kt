package com.timearchive.adapter.outbound.storage

import com.timearchive.domain.port.MediaInspectionPort
import com.timearchive.domain.port.MediaObjectStoragePort
import org.springframework.stereotype.Component

@Component
class Mp4MediaInspectionAdapter(
    private val mediaObjectStoragePort: MediaObjectStoragePort,
    private val mp4DurationParser: Mp4DurationParser = Mp4DurationParser(),
) : MediaInspectionPort {
    override fun inspect(command: MediaInspectionPort.Command): MediaInspectionPort.Result {
        if (command.contentType != "video/mp4") {
            return MediaInspectionPort.Result(durationMs = null)
        }

        val durationMs = mediaObjectStoragePort.openObject(command.objectKey)?.use { input ->
            runCatching { mp4DurationParser.parseDurationMs(input) }
                .getOrElse { throw IllegalArgumentException("uploaded video duration metadata not found", it) }
        } ?: throw IllegalStateException("uploaded media object not found")

        return MediaInspectionPort.Result(durationMs = durationMs)
    }
}
