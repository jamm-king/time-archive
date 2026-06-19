package com.timearchive.application

import com.timearchive.domain.model.ArchiveTimeline
import com.timearchive.domain.model.PublicTimelineSegment
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaObjectStoragePort
import com.timearchive.domain.port.PublicTimelineSegmentRepository
import java.time.Duration
import java.time.Instant

class ListPublicTimelineSegments(
    private val publicTimelineSegmentRepository: PublicTimelineSegmentRepository,
    private val mediaObjectStoragePort: MediaObjectStoragePort,
    private val clockPort: ClockPort,
    private val playbackUrlTtl: Duration,
) {
    fun list(query: Query): Result {
        val range = ArchiveTimeline.requireWithin(
            TimeRange(startSecond = query.from, endSecond = query.to),
        )
        val expiresAt = clockPort.now().plus(playbackUrlTtl)

        return Result(
            range = range,
            segments = publicTimelineSegmentRepository.findApprovedOverlapping(range)
                .map { segment -> segment.withPresignedMediaUrls(expiresAt) },
        )
    }

    private fun PublicTimelineSegment.withPresignedMediaUrls(
        expiresAt: Instant,
    ): PublicTimelineSegment =
        copy(
            mediaUrl = createPresignedDownloadUrl(fileUrl = mediaUrl, expiresAt = expiresAt),
            thumbnailUrl = thumbnailUrl?.let { fileUrl ->
                createPresignedDownloadUrl(fileUrl = fileUrl, expiresAt = expiresAt)
            },
        )

    private fun createPresignedDownloadUrl(
        fileUrl: String,
        expiresAt: Instant,
    ): String =
        mediaObjectStoragePort.createPresignedDownload(
            MediaObjectStoragePort.DownloadCommand(
                fileUrl = fileUrl,
                expiresAt = expiresAt,
            ),
        ).downloadUrl

    data class Query(
        val from: Long,
        val to: Long,
    )

    data class Result(
        val range: TimeRange,
        val segments: List<PublicTimelineSegment>,
    )
}
