package com.timearchive.adapter.inbound.web

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RequestCorrelationFilterTest {
    @AfterEach
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun generatesRequestIdWhenHeaderIsMissing() {
        val filter = RequestCorrelationFilter()

        val result = execute(filter)

        assertThat(result.chainInvoked).isTrue()
        assertThat(result.requestIdInChain).isNotBlank()
        assertThat(result.request.getAttribute(RequestCorrelationFilter.REQUEST_ATTRIBUTE))
            .isEqualTo(result.requestIdInChain)
        assertThat(result.response.getHeader(RequestCorrelationFilter.HEADER_NAME))
            .isEqualTo(result.requestIdInChain)
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull()
    }

    @Test
    fun acceptsValidRequestIdHeader() {
        val filter = RequestCorrelationFilter()

        val result = execute(filter, requestId = "client-request-123")

        assertThat(result.requestIdInChain).isEqualTo("client-request-123")
        assertThat(result.response.getHeader(RequestCorrelationFilter.HEADER_NAME))
            .isEqualTo("client-request-123")
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull()
    }

    @Test
    fun replacesInvalidRequestIdHeader() {
        val filter = RequestCorrelationFilter()

        val result = execute(filter, requestId = "bad request id with spaces")

        assertThat(result.requestIdInChain).isNotBlank()
        assertThat(result.requestIdInChain).isNotEqualTo("bad request id with spaces")
        assertThat(result.response.getHeader(RequestCorrelationFilter.HEADER_NAME))
            .isEqualTo(result.requestIdInChain)
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull()
    }

    @Test
    fun clearsMdcWhenDownstreamThrows() {
        val filter = RequestCorrelationFilter()
        val request = MockHttpServletRequest("GET", "/api/test")
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> throw IllegalStateException("downstream failed") }

        assertThatThrownBy {
            filter.doFilter(request, response, chain)
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER_NAME)).isNotBlank()
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull()
    }

    private fun execute(
        filter: RequestCorrelationFilter,
        requestId: String? = null,
    ): FilterResult {
        val request = MockHttpServletRequest("GET", "/api/test").apply {
            requestId?.let { addHeader(RequestCorrelationFilter.HEADER_NAME, it) }
        }
        val response = MockHttpServletResponse()
        var chainInvoked = false
        var requestIdInChain: String? = null
        val chain = FilterChain { _, _ ->
            chainInvoked = true
            requestIdInChain = MDC.get(RequestCorrelationFilter.MDC_KEY)
        }

        filter.doFilter(request, response, chain)

        return FilterResult(
            request = request,
            response = response,
            chainInvoked = chainInvoked,
            requestIdInChain = requestIdInChain,
        )
    }

    private data class FilterResult(
        val request: MockHttpServletRequest,
        val response: MockHttpServletResponse,
        val chainInvoked: Boolean,
        val requestIdInChain: String?,
    )
}
