package com.timearchive.adapter.inbound.rest

import com.timearchive.application.ListPublicTimelineSegments
import com.timearchive.domain.model.PublicTimelineSegment
import java.util.UUID

data class PublicTimelineResponse(
    val from: Long,
    val to: Long,
    val segments: List<PublicTimelineSegmentResponse>,
) {
    companion object {
        fun from(result: ListPublicTimelineSegments.Result): PublicTimelineResponse =
            PublicTimelineResponse(
                from = result.range.startSecond,
                to = result.range.endSecond,
                segments = result.segments.map(PublicTimelineSegmentResponse::from),
            )
    }
}

data class PublicTimelineSegmentResponse(
    val startSecond: Long,
    val endSecond: Long,
    val mediaAssetId: UUID,
    val mediaType: String,
    val mediaUrl: String,
    val thumbnailUrl: String?,
    val externalLink: String?,
) {
    companion object {
        fun from(segment: PublicTimelineSegment): PublicTimelineSegmentResponse =
            PublicTimelineSegmentResponse(
                startSecond = segment.range.startSecond,
                endSecond = segment.range.endSecond,
                mediaAssetId = segment.mediaAssetId,
                mediaType = segment.mediaType.name,
                mediaUrl = segment.mediaUrl,
                thumbnailUrl = segment.thumbnailUrl,
                externalLink = segment.externalLink,
            )
    }
}
