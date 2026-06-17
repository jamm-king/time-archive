package com.timearchive.domain.model

import java.util.UUID

data class PublicTimelineSegment(
    val range: TimeRange,
    val mediaAssetId: UUID,
    val mediaType: MediaType,
    val mediaUrl: String,
    val thumbnailUrl: String?,
    val externalLink: String?,
) {
    init {
        ArchiveTimeline.requireWithin(range)
        require(mediaUrl.isNotBlank()) { "mediaUrl must not be blank" }
    }
}
