package com.timearchive.adapter.outbound.ratelimit

import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.RateLimitPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import java.time.Duration
import java.time.Instant

class RedisRateLimitAdapterTest {
    private val redisTemplate: StringRedisTemplate = mockk()
    private val now = Instant.parse("2026-06-22T00:00:45Z")
    private val adapter = RedisRateLimitAdapter(redisTemplate, ClockPort { now })

    @Test
    fun allowsRequestsWithinTheFixedWindowLimit() {
        every {
            redisTemplate.execute(
                any<RedisScript<Long>>(),
                any<List<String>>(),
                *anyVararg(),
            )
        } returns 3L

        val decision = adapter.consume(command(limit = 5, window = Duration.ofMinutes(1)))

        assertThat(decision.allowed).isTrue()
        assertThat(decision.limit).isEqualTo(5)
        assertThat(decision.remaining).isEqualTo(2)
        assertThat(decision.retryAfterSeconds).isEqualTo(15)
    }

    @Test
    fun deniesRequestsAboveTheFixedWindowLimit() {
        every {
            redisTemplate.execute(
                any<RedisScript<Long>>(),
                any<List<String>>(),
                *anyVararg(),
            )
        } returns 6L

        val decision = adapter.consume(command(limit = 5, window = Duration.ofMinutes(1)))

        assertThat(decision.allowed).isFalse()
        assertThat(decision.remaining).isZero()
        assertThat(decision.retryAfterSeconds).isEqualTo(15)
    }

    @Test
    fun usesScopeSubjectHashAndWindowIdInRedisKey() {
        val keys = slot<List<String>>()
        every {
            redisTemplate.execute(
                any<RedisScript<Long>>(),
                capture(keys),
                *anyVararg(),
            )
        } returns 1L

        adapter.consume(command(limit = 5, window = Duration.ofMinutes(1)))

        assertThat(keys.captured).containsExactly(
            "time-archive:rate-limit:auth-login:subject-hash:" + (now.epochSecond / 60),
        )
    }

    private fun command(limit: Long, window: Duration): RateLimitPort.Command =
        RateLimitPort.Command(
            scope = "auth-login",
            subjectHash = "subject-hash",
            limit = limit,
            window = window,
        )
}
