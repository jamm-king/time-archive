package com.timearchive.adapter.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

class RequestCorrelationFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(HEADER_NAME)
            ?.trim()
            ?.takeIf(::isValidRequestId)
            ?: UUID.randomUUID().toString()

        request.setAttribute(REQUEST_ATTRIBUTE, requestId)
        response.setHeader(HEADER_NAME, requestId)
        MDC.put(MDC_KEY, requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val HEADER_NAME = "X-Request-Id"
        const val REQUEST_ATTRIBUTE = "timeArchive.requestId"
        const val MDC_KEY = "requestId"

        private val requestIdPattern = Regex("^[A-Za-z0-9._-]{8,128}$")

        fun currentRequestId(): String? = MDC.get(MDC_KEY)

        fun requestIdFrom(request: HttpServletRequest): String? =
            request.getAttribute(REQUEST_ATTRIBUTE) as? String

        fun isValidRequestId(value: String): Boolean = requestIdPattern.matches(value)
    }
}
