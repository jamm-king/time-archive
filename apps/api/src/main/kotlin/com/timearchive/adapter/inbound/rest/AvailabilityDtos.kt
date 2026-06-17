package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CheckAvailability

data class AvailabilityResponse(
    val startSecond: Long,
    val endSecond: Long,
    val available: Boolean,
    val conflicts: List<AvailabilityConflictResponse>,
) {
    companion object {
        fun from(result: CheckAvailability.Result): AvailabilityResponse =
            AvailabilityResponse(
                startSecond = result.range.startSecond,
                endSecond = result.range.endSecond,
                available = result.available,
                conflicts = result.conflicts.map { AvailabilityConflictResponse.from(it) },
            )
    }
}

data class AvailabilityConflictResponse(
    val type: String,
    val startSecond: Long,
    val endSecond: Long,
) {
    companion object {
        fun from(conflict: CheckAvailability.Conflict): AvailabilityConflictResponse =
            AvailabilityConflictResponse(
                type = conflict.type.name,
                startSecond = conflict.range.startSecond,
                endSecond = conflict.range.endSecond,
            )
    }
}
