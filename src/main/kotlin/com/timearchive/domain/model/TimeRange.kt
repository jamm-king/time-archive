package com.timearchive.domain.model

data class TimeRange(
    val startSecond: Long,
    val endSecond: Long,
) {
    init {
        require(startSecond >= 0) { "startSecond must be greater than or equal to 0" }
        require(endSecond > startSecond) { "endSecond must be greater than startSecond" }
    }

    val durationSeconds: Long
        get() = endSecond - startSecond

    fun contains(second: Long): Boolean = second >= startSecond && second < endSecond

    fun overlaps(other: TimeRange): Boolean =
        startSecond < other.endSecond && other.startSecond < endSecond

    fun requireWithin(totalSeconds: Long): TimeRange {
        require(totalSeconds > 0) { "totalSeconds must be greater than 0" }
        require(endSecond <= totalSeconds) { "endSecond must be less than or equal to totalSeconds" }
        return this
    }
}
