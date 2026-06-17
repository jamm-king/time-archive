package com.timearchive.domain.port

import com.timearchive.domain.model.PublicTimelineSegment
import com.timearchive.domain.model.TimeRange

interface PublicTimelineSegmentRepository {
    fun findApprovedOverlapping(range: TimeRange): List<PublicTimelineSegment>
}
