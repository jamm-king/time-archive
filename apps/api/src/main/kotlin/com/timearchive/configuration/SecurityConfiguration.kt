package com.timearchive.configuration

import com.timearchive.adapter.inbound.rest.CurrentUserSession
import com.timearchive.adapter.inbound.security.SessionAuthenticationFilter
import com.timearchive.domain.port.UserAccountRepository
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
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository

@Configuration
@EnableWebSecurity
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        sessionAuthenticationFilter: SessionAuthenticationFilter,
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
                it.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpStatus.UNAUTHORIZED.value()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write(
                        """{"code":"AUTHENTICATION_REQUIRED","message":"Authentication required","details":[]}""",
                    )
                }
                it.accessDeniedHandler { _, response, _ ->
                    response.status = HttpStatus.FORBIDDEN.value()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write(
                        """{"code":"CSRF_TOKEN_INVALID","message":"CSRF token is missing or invalid","details":[]}""",
                    )
                }
            }
            .addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter::class.java)
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
}
