package com.timearchive.adapter.inbound.security

import com.timearchive.configuration.RateLimitProperties
import com.timearchive.domain.port.RateLimitPort
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class ApiRateLimitingFilterTest {
    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun appliesConfiguredPoliciesToProtectedEndpointGroups() {
        val port = RecordingRateLimitPort()
        val filter = ApiRateLimitingFilter(port, RateLimitProperties())

        execute(filter, "POST", "/api/auth/register")
        execute(filter, "POST", "/api/auth/login")
        execute(filter, "GET", "/api/timeline")
        execute(filter, "GET", "/api/archive/availability")
        execute(filter, "POST", "/api/purchase/reservations")
        execute(filter, "POST", "/api/owned-ranges/record-id/media/upload-requests")
        execute(filter, "GET", "/api/admin/media/assets")

        assertThat(port.commands.map { it.scope }).containsExactly(
            "auth-register",
            "auth-login",
            "public-read",
            "public-read",
            "purchase",
            "media-mutation",
            "admin",
        )
    }

    @Test
    fun doesNotLimitUnmatchedEndpoints() {
        val port = RecordingRateLimitPort()
        val filter = ApiRateLimitingFilter(port, RateLimitProperties())

        val result = execute(filter, "GET", "/api/me")
        val preflight = execute(filter, "OPTIONS", "/api/admin/media/assets")

        assertThat(result.chainInvoked).isTrue()
        assertThat(preflight.chainInvoked).isTrue()
        assertThat(port.commands).isEmpty()
    }

    @Test
    fun hashesConfiguredClientIpHeaderInsteadOfStoringRawAddress() {
        val port = RecordingRateLimitPort()
        val properties = RateLimitProperties(clientIpHeader = "CF-Connecting-IP")
        val filter = ApiRateLimitingFilter(port, properties)

        execute(
            filter = filter,
            method = "POST",
            path = "/api/auth/login",
            remoteAddress = "172.18.0.1",
            headers = mapOf("CF-Connecting-IP" to "203.0.113.10"),
        )

        assertThat(port.commands.single().subjectHash)
            .hasSize(64)
            .doesNotContain("203.0.113.10")
            .doesNotContain("172.18.0.1")
    }

    @Test
    fun usesAuthenticatedUserIdentityForPurchaseRequests() {
        val port = RecordingRateLimitPort()
        val filter = ApiRateLimitingFilter(port, RateLimitProperties())
        val principal = AuthenticatedUser(
            userId = "user-123",
            email = "buyer@example.com",
            displayName = "Buyer",
            role = "USER",
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )

        execute(filter, "POST", "/api/purchase/reservations", remoteAddress = "203.0.113.10")
        execute(filter, "POST", "/api/purchase/reservations", remoteAddress = "203.0.113.11")

        assertThat(port.commands.map { it.subjectHash }.distinct()).hasSize(1)
    }

    @Test
    fun returnsTooManyRequestsWithRetryMetadataWhenDenied() {
        val port = RecordingRateLimitPort(
            decision = RateLimitPort.Decision(
                allowed = false,
                limit = 10,
                remaining = 0,
                retryAfterSeconds = 17,
            ),
        )
        val filter = ApiRateLimitingFilter(port, RateLimitProperties())

        val result = execute(filter, "POST", "/api/auth/login")

        assertThat(result.chainInvoked).isFalse()
        assertThat(result.response.status).isEqualTo(429)
        assertThat(result.response.getHeader("Retry-After")).isEqualTo("17")
        assertThat(result.response.getHeader("X-RateLimit-Limit")).isEqualTo("10")
        assertThat(result.response.getHeader("X-RateLimit-Remaining")).isEqualTo("0")
        assertThat(result.response.contentAsString).contains("\"code\":\"RATE_LIMIT_EXCEEDED\"")
    }

    @Test
    fun failsClosedWhenRateLimitStorageIsUnavailable() {
        val port = RecordingRateLimitPort(failure = IllegalStateException("redis unavailable"))
        val filter = ApiRateLimitingFilter(port, RateLimitProperties())

        val result = execute(filter, "GET", "/api/timeline")

        assertThat(result.chainInvoked).isFalse()
        assertThat(result.response.status).isEqualTo(503)
        assertThat(result.response.contentAsString).contains("\"code\":\"RATE_LIMIT_UNAVAILABLE\"")
    }

    @Test
    fun bypassesRateLimitingWhenDisabled() {
        val port = RecordingRateLimitPort()
        val filter = ApiRateLimitingFilter(port, RateLimitProperties(enabled = false))

        val result = execute(filter, "POST", "/api/auth/login")

        assertThat(result.chainInvoked).isTrue()
        assertThat(port.commands).isEmpty()
    }

    private fun execute(
        filter: ApiRateLimitingFilter,
        method: String,
        path: String,
        remoteAddress: String = "127.0.0.1",
        headers: Map<String, String> = emptyMap(),
    ): FilterResult {
        val request = MockHttpServletRequest(method, path).apply {
            remoteAddr = remoteAddress
            headers.forEach(::addHeader)
        }
        val response = MockHttpServletResponse()
        var chainInvoked = false
        val chain = FilterChain { _, _ -> chainInvoked = true }

        filter.doFilter(request, response, chain)

        return FilterResult(response, chainInvoked)
    }

    private data class FilterResult(
        val response: MockHttpServletResponse,
        val chainInvoked: Boolean,
    )

    private class RecordingRateLimitPort(
        private val decision: RateLimitPort.Decision = RateLimitPort.Decision(
            allowed = true,
            limit = 100,
            remaining = 99,
            retryAfterSeconds = 60,
        ),
        private val failure: Exception? = null,
    ) : RateLimitPort {
        val commands = mutableListOf<RateLimitPort.Command>()

        override fun consume(command: RateLimitPort.Command): RateLimitPort.Decision {
            commands += command
            failure?.let { throw it }
            return decision
        }
    }
}
