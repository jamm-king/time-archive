package com.timearchive.domain.model

object ArchiveTimeline {
    const val TOTAL_SECONDS: Long = 86_400

    fun requireWithin(range: TimeRange): TimeRange = range.requireWithin(TOTAL_SECONDS)
}
