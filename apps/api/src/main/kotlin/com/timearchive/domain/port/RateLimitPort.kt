package com.timearchive.domain.port

import java.time.Duration

fun interface RateLimitPort {
    fun consume(command: Command): Decision

    data class Command(
        val scope: String,
        val subjectHash: String,
        val limit: Long,
        val window: Duration,
    ) {
        init {
            require(scope.isNotBlank()) { "rate limit scope must not be blank" }
            require(subjectHash.isNotBlank()) { "rate limit subject hash must not be blank" }
            require(limit > 0) { "rate limit must be greater than zero" }
            require(!window.isNegative && !window.isZero) { "rate limit window must be positive" }
        }
    }

    data class Decision(
        val allowed: Boolean,
        val limit: Long,
        val remaining: Long,
        val retryAfterSeconds: Long,
    ) {
        init {
            require(limit > 0) { "rate limit must be greater than zero" }
            require(remaining >= 0) { "rate limit remaining must not be negative" }
            require(retryAfterSeconds > 0) { "rate limit retry after must be positive" }
        }
    }
}
