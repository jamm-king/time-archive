package com.timearchive.domain.model

import java.time.Instant
import java.util.UUID

data class AuditLog(
    val id: UUID,
    val actorUserId: UUID?,
    val actorType: String,
    val action: String,
    val resourceType: String,
    val resourceId: UUID,
    val beforeState: String?,
    val afterState: String?,
    val requestId: String?,
    val createdAt: Instant,
) {
    init {
        require(actorType.isNotBlank()) { "actorType must not be blank" }
        require(action.isNotBlank()) { "action must not be blank" }
        require(resourceType.isNotBlank()) { "resourceType must not be blank" }
    }
}
