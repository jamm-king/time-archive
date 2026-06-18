package com.timearchive.adapter.inbound.rest

import com.timearchive.application.AuthenticateUser
import com.timearchive.application.GetCurrentUser
import com.timearchive.application.RegisterUser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthController(
    private val registerUser: RegisterUser,
    private val authenticateUser: AuthenticateUser,
    private val getCurrentUser: GetCurrentUser,
    private val currentUserSession: CurrentUserSession,
) {
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        session: HttpSession,
    ): CurrentUserResponse {
        val user = registerUser.register(
            RegisterUser.Command(
                email = requireNotNull(request.email),
                password = requireNotNull(request.password),
                displayName = requireNotNull(request.displayName),
            ),
        )
        currentUserSession.signIn(session, user.id)

        return CurrentUserResponse.from(user)
    }

    @PostMapping("/auth/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        session: HttpSession,
    ): CurrentUserResponse {
        val user = authenticateUser.authenticate(
            AuthenticateUser.Command(
                email = requireNotNull(request.email),
                password = requireNotNull(request.password),
            ),
        )
        currentUserSession.signIn(session, user.id)

        return CurrentUserResponse.from(user)
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(request: HttpServletRequest) {
        request.getSession(false)?.invalidate()
    }

    @GetMapping("/me")
    fun me(request: HttpServletRequest): CurrentUserResponse {
        val userId = currentUserSession.currentUserId(request)
            ?: throw IllegalArgumentException("authentication required")

        return CurrentUserResponse.from(getCurrentUser.get(GetCurrentUser.Query(userId = userId)))
    }
}
