package com.timearchive.application

import com.timearchive.domain.model.ArchiveTimeline
import com.timearchive.domain.model.PublicTimelineSegment
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.PublicTimelineSegmentRepository

class ListPublicTimelineSegments(
    private val publicTimelineSegmentRepository: PublicTimelineSegmentRepository,
) {
    fun list(query: Query): Result {
        val range = ArchiveTimeline.requireWithin(
            TimeRange(startSecond = query.from, endSecond = query.to),
        )

        return Result(
            range = range,
            segments = publicTimelineSegmentRepository.findApprovedOverlapping(range),
        )
    }

    data class Query(
        val from: Long,
        val to: Long,
    )

    data class Result(
        val range: TimeRange,
        val segments: List<PublicTimelineSegment>,
    )
}
