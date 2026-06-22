package com.timearchive.adapter.inbound.security

import com.timearchive.configuration.RateLimitProperties
import com.timearchive.domain.port.RateLimitPort
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class ApiRateLimitingFilter(
    private val rateLimitPort: RateLimitPort,
    private val properties: RateLimitProperties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val policy = policyFor(request)
        if (!properties.enabled || policy == null) {
            filterChain.doFilter(request, response)
            return
        }

        val subject = when (policy.subjectType) {
            SubjectType.CLIENT_IP -> clientIp(request)
            SubjectType.USER_OR_CLIENT_IP -> authenticatedUserId() ?: clientIp(request)
        }
        val command = RateLimitPort.Command(
            scope = policy.scope,
            subjectHash = hmacSha256(subject),
            limit = policy.policy.limit,
            window = policy.policy.window,
        )
        val decision = try {
            rateLimitPort.consume(command)
        } catch (exception: Exception) {
            log.warn("Rate limit evaluation failed for scope=${policy.scope}", exception)
            writeError(
                response = response,
                status = HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                code = "RATE_LIMIT_UNAVAILABLE",
                message = "Rate limit service is unavailable",
            )
            return
        }

        response.setHeader("X-RateLimit-Limit", decision.limit.toString())
        response.setHeader("X-RateLimit-Remaining", decision.remaining.toString())
        response.setHeader("X-RateLimit-Reset", decision.retryAfterSeconds.toString())

        if (!decision.allowed) {
            response.setHeader("Retry-After", decision.retryAfterSeconds.toString())
            writeError(
                response = response,
                status = 429,
                code = "RATE_LIMIT_EXCEEDED",
                message = "Too many requests",
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun policyFor(request: HttpServletRequest): AppliedPolicy? {
        val method = request.method
        val path = request.requestURI.removePrefix(request.contextPath)

        return when {
            method == "POST" && path == "/api/auth/register" ->
                AppliedPolicy("auth-register", properties.registration, SubjectType.CLIENT_IP)
            method == "POST" && path == "/api/auth/login" ->
                AppliedPolicy("auth-login", properties.login, SubjectType.CLIENT_IP)
            method == "GET" && (path == "/api/timeline" || path == "/api/archive/availability") ->
                AppliedPolicy("public-read", properties.publicRead, SubjectType.CLIENT_IP)
            method == "POST" && path.startsWith("/api/purchase/") ->
                AppliedPolicy("purchase", properties.purchase, SubjectType.USER_OR_CLIENT_IP)
            method == "POST" && path.startsWith("/api/owned-ranges/") && path.contains("/media") ->
                AppliedPolicy("media-mutation", properties.mediaMutation, SubjectType.USER_OR_CLIENT_IP)
            (method == "GET" || method == "POST") && path.startsWith("/api/admin/") ->
                AppliedPolicy("admin", properties.admin, SubjectType.USER_OR_CLIENT_IP)
            else -> null
        }
    }

    private fun authenticatedUserId(): String? =
        (SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser)?.userId

    private fun clientIp(request: HttpServletRequest): String {
        val configuredHeader = properties.clientIpHeader.trim()
        if (configuredHeader.isNotEmpty()) {
            request.getHeader(configuredHeader)
                ?.substringBefore(',')
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { return it }
        }
        return request.remoteAddr.orEmpty().ifBlank { "unknown" }
    }

    private fun hmacSha256(value: String): String =
        Mac.getInstance("HmacSHA256")
            .apply {
                init(SecretKeySpec(properties.keySalt.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            }
            .doFinal(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun writeError(
        response: HttpServletResponse,
        status: Int,
        code: String,
        message: String,
    ) {
        response.status = status
        response.contentType = "application/json"
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.writer.write("""{"code":"$code","message":"$message","details":[]}""")
    }

    private data class AppliedPolicy(
        val scope: String,
        val policy: RateLimitProperties.Policy,
        val subjectType: SubjectType,
    )

    private enum class SubjectType {
        CLIENT_IP,
        USER_OR_CLIENT_IP,
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApiRateLimitingFilter::class.java)
    }
}
