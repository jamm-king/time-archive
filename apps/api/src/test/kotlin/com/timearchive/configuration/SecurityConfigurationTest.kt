package com.timearchive.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.cors.CorsConfigurationSource

class SecurityConfigurationTest {
    private val configuration = SecurityConfiguration()

    @Test
    fun `cors configuration source exists for api paths without permissive origins`() {
        // Given
        val source: CorsConfigurationSource = configuration.corsConfigurationSource()
        val request = MockHttpServletRequest("GET", "/api/timeline")

        // When
        val corsConfiguration = source.getCorsConfiguration(request)

        // Then
        assertThat(corsConfiguration).isNotNull
        assertThat(corsConfiguration?.allowedOrigins).isNull()
        assertThat(corsConfiguration?.allowedOriginPatterns).isNull()
        assertThat(corsConfiguration?.allowedMethods).isNull()
        assertThat(corsConfiguration?.allowedHeaders).isNull()
    }
}
