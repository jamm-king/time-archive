package com.timearchive.adapter.outbound.ratelimit

import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.RateLimitPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant

class RedisRateLimitAdapterIntegrationTest {
    @Test
    fun atomicallyLimitsRequestsAndAllowsTheNextWindow() {
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379)
        redis.start()

        val connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379))
        connectionFactory.afterPropertiesSet()

        try {
            var now = Instant.parse("2026-06-22T00:00:45Z")
            val adapter = RedisRateLimitAdapter(
                redisTemplate = StringRedisTemplate(connectionFactory),
                clockPort = ClockPort { now },
            )
            val command = RateLimitPort.Command(
                scope = "integration",
                subjectHash = "subject-hash",
                limit = 2,
                window = Duration.ofMinutes(1),
            )

            assertThat(adapter.consume(command).allowed).isTrue()
            assertThat(adapter.consume(command).allowed).isTrue()
            assertThat(adapter.consume(command).allowed).isFalse()

            now = now.plusSeconds(60)

            val nextWindow = adapter.consume(command)
            assertThat(nextWindow.allowed).isTrue()
            assertThat(nextWindow.remaining).isEqualTo(1)
        } finally {
            connectionFactory.destroy()
            redis.stop()
        }
    }
}
