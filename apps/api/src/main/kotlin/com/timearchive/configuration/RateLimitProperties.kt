package com.timearchive.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("time-archive.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    val clientIpHeader: String = "",
    val keySalt: String = "time-archive-local-rate-limit",
    val registration: Policy = Policy(limit = 5, window = Duration.ofMinutes(10)),
    val login: Policy = Policy(limit = 10, window = Duration.ofMinutes(1)),
    val publicRead: Policy = Policy(limit = 120, window = Duration.ofMinutes(1)),
    val purchase: Policy = Policy(limit = 30, window = Duration.ofMinutes(1)),
    val mediaMutation: Policy = Policy(limit = 30, window = Duration.ofMinutes(1)),
    val admin: Policy = Policy(limit = 60, window = Duration.ofMinutes(1)),
) {
    init {
        require(keySalt.isNotBlank()) { "rate limit key salt must not be blank" }
    }

    data class Policy(
        val limit: Long,
        val window: Duration,
    ) {
        init {
            require(limit > 0) { "rate limit must be greater than zero" }
            require(!window.isNegative && !window.isZero) { "rate limit window must be positive" }
        }
    }
}
