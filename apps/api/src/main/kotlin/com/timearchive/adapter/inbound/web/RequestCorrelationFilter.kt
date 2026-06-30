package com.timearchive.adapter.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.TimeUnit
import java.util.UUID

class RequestCorrelationFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAtNanos = System.nanoTime()
        val requestId = request.getHeader(HEADER_NAME)
            ?.trim()
            ?.takeIf(::isValidRequestId)
            ?: UUID.randomUUID().toString()
        var failure: Throwable? = null

        request.setAttribute(REQUEST_ATTRIBUTE, requestId)
        response.setHeader(HEADER_NAME, requestId)
        MDC.put(MDC_KEY, requestId)

        try {
            filterChain.doFilter(request, response)
        } catch (ex: Throwable) {
            failure = ex
            throw ex
        } finally {
            logRequestCompletion(
                request = request,
                response = response,
                requestId = requestId,
                durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos),
                failure = failure,
            )
            MDC.remove(MDC_KEY)
        }
    }

    private fun logRequestCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        requestId: String,
        durationMs: Long,
        failure: Throwable?,
    ) {
        val method = request.method ?: "UNKNOWN"
        val path = request.requestURI ?: "/"
        val status = response.status

        if (failure == null) {
            log.info(
                "api request completed requestId={} method={} path={} status={} durationMs={}",
                requestId,
                method,
                path,
                status,
                durationMs,
            )
        } else {
            log.warn(
                "api request completed requestId={} method={} path={} status={} durationMs={} exception={}",
                requestId,
                method,
                path,
                status,
                durationMs,
                failure.javaClass.simpleName,
            )
        }
    }

    companion object {
        const val HEADER_NAME = "X-Request-Id"
        const val REQUEST_ATTRIBUTE = "timeArchive.requestId"
        const val MDC_KEY = "requestId"

        private val requestIdPattern = Regex("^[A-Za-z0-9._-]{8,128}$")
        private val log = LoggerFactory.getLogger(RequestCorrelationFilter::class.java)

        fun currentRequestId(): String? = MDC.get(MDC_KEY)

        fun requestIdFrom(request: HttpServletRequest): String? =
            request.getAttribute(REQUEST_ATTRIBUTE) as? String

        fun isValidRequestId(value: String): Boolean = requestIdPattern.matches(value)
    }
}
