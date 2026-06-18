package com.timearchive.adapter.inbound.security

import com.timearchive.adapter.inbound.rest.CurrentUserSession
import com.timearchive.domain.model.UserAccount
import com.timearchive.domain.port.UserAccountRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class SessionAuthenticationFilter(
    private val currentUserSession: CurrentUserSession,
    private val userAccountRepository: UserAccountRepository,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val user = currentUserSession.currentUserId(request)
            ?.let(userAccountRepository::findById)

        if (user != null) {
            val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken.authenticated(user.toPrincipal(), null, authorities)
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun UserAccount.toPrincipal(): AuthenticatedUser =
        AuthenticatedUser(
            userId = id.toString(),
            email = email,
            displayName = displayName,
            role = role.name,
        )
}

data class AuthenticatedUser(
    val userId: String,
    val email: String,
    val displayName: String,
    val role: String,
)
