package com.timearchive.domain.model

import java.time.Instant
import java.util.UUID

data class OutboxEvent(
    val id: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: UUID,
    val payload: String,
    val status: String,
    val createdAt: Instant,
    val processedAt: Instant?,
    val retryCount: Int,
    val lastError: String?,
) {
    init {
        require(eventType.isNotBlank()) { "eventType must not be blank" }
        require(aggregateType.isNotBlank()) { "aggregateType must not be blank" }
        require(payload.isNotBlank()) { "payload must not be blank" }
        require(status.isNotBlank()) { "status must not be blank" }
        require(retryCount >= 0) { "retryCount must be greater than or equal to 0" }
    }

    companion object {
        const val PENDING = "PENDING"
    }
}
