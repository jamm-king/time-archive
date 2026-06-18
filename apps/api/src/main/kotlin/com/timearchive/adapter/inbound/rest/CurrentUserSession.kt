package com.timearchive.adapter.inbound.rest

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import java.util.UUID

class CurrentUserSession {
    fun signIn(session: HttpSession, userId: UUID) {
        session.setAttribute(CURRENT_USER_ID_SESSION_ATTRIBUTE, userId.toString())
    }

    fun currentUserId(request: HttpServletRequest): UUID? =
        request.getSession(false)
            ?.getAttribute(CURRENT_USER_ID_SESSION_ATTRIBUTE)
            ?.toString()
            ?.let(UUID::fromString)

    companion object {
        const val CURRENT_USER_ID_SESSION_ATTRIBUTE = "timeArchiveCurrentUserId"
    }
}
