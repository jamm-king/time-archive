package com.timearchive.configuration

import com.timearchive.adapter.inbound.rest.CurrentUserSession
import com.timearchive.adapter.inbound.security.ApiRateLimitingFilter
import com.timearchive.adapter.inbound.security.SessionAuthenticationFilter
import com.timearchive.adapter.inbound.web.RequestCorrelationFilter
import com.timearchive.domain.port.UserAccountRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        requestCorrelationFilter: RequestCorrelationFilter,
        sessionAuthenticationFilter: SessionAuthenticationFilter,
        apiRateLimitingFilter: ApiRateLimitingFilter,
    ): SecurityFilterChain =
        http
            .csrf {
                it
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(
                        "/api/internal/payments/fake/webhooks/primary-purchase-completed",
                    )
            }
            .cors(Customizer.withDefaults())
            .sessionManagement {
                it
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation { sessionFixation -> sessionFixation.none() }
            }
            .exceptionHandling {
                it.authenticationEntryPoint { request, response, _ ->
                    response.status = HttpStatus.UNAUTHORIZED.value()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write(errorBody("AUTHENTICATION_REQUIRED", "Authentication required", request))
                }
                it.accessDeniedHandler { request, response, _ ->
                    response.status = HttpStatus.FORBIDDEN.value()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write(errorBody("CSRF_TOKEN_INVALID", "CSRF token is missing or invalid", request))
                }
            }
            .addFilterBefore(requestCorrelationFilter, AnonymousAuthenticationFilter::class.java)
            .addFilterAfter(sessionAuthenticationFilter, RequestCorrelationFilter::class.java)
            .addFilterAfter(apiRateLimitingFilter, SessionAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                it
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/me").authenticated()
                    .requestMatchers("/api/me/**").authenticated()
                    .requestMatchers("/api/**").permitAll()
                    .anyRequest().denyAll()
            }
            .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", CorsConfiguration())
        }

    @Bean
    fun currentUserSession(): CurrentUserSession = CurrentUserSession()

    @Bean
    fun sessionAuthenticationFilter(
        currentUserSession: CurrentUserSession,
        userAccountRepository: UserAccountRepository,
    ): SessionAuthenticationFilter =
        SessionAuthenticationFilter(
            currentUserSession = currentUserSession,
            userAccountRepository = userAccountRepository,
        )

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(): UserDetailsService = InMemoryUserDetailsManager()

    @Bean
    fun requestCorrelationFilter(): RequestCorrelationFilter = RequestCorrelationFilter()

    private fun errorBody(
        code: String,
        message: String,
        request: HttpServletRequest,
    ): String {
        val requestId = RequestCorrelationFilter.requestIdFrom(request)
        return """{"code":"$code","message":"$message","details":[],"requestId":${requestIdJson(requestId)}}"""
    }

    private fun requestIdJson(requestId: String?): String =
        requestId
            ?.takeIf(RequestCorrelationFilter::isValidRequestId)
            ?.let { "\"$it\"" }
            ?: "null"
}
