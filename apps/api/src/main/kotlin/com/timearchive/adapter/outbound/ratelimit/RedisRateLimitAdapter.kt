package com.timearchive.adapter.outbound.ratelimit

import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.RateLimitPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

@Component
class RedisRateLimitAdapter(
    private val redisTemplate: StringRedisTemplate,
    private val clockPort: ClockPort,
) : RateLimitPort {
    override fun consume(command: RateLimitPort.Command): RateLimitPort.Decision {
        val windowSeconds = command.window.seconds.coerceAtLeast(1)
        val epochSecond = clockPort.now().epochSecond
        val windowId = Math.floorDiv(epochSecond, windowSeconds)
        val key = "$KEY_PREFIX:${command.scope}:${command.subjectHash}:$windowId"
        val current = requireNotNull(
            redisTemplate.execute(INCREMENT_SCRIPT, listOf(key), windowSeconds.toString()),
        ) { "rate limit counter returned no result" }
        val retryAfterSeconds = windowSeconds - Math.floorMod(epochSecond, windowSeconds)

        return RateLimitPort.Decision(
            allowed = current <= command.limit,
            limit = command.limit,
            remaining = (command.limit - current).coerceAtLeast(0),
            retryAfterSeconds = retryAfterSeconds,
        )
    }

    companion object {
        private const val KEY_PREFIX = "time-archive:rate-limit"
        private val INCREMENT_SCRIPT = DefaultRedisScript(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """.trimIndent(),
            Long::class.java,
        )
    }
}
