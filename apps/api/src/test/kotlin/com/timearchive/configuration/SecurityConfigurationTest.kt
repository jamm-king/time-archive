package com.timearchive.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.provisioning.InMemoryUserDetailsManager
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

    @Test
    fun `user details service is empty to prevent generated default password logging`() {
        // When
        val userDetailsService = configuration.userDetailsService()

        // Then
        assertThat(userDetailsService).isInstanceOf(InMemoryUserDetailsManager::class.java)
        assertThat((userDetailsService as InMemoryUserDetailsManager).userExists("user")).isFalse()
    }
}
