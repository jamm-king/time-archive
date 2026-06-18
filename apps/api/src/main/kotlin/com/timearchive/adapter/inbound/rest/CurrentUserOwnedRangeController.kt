package com.timearchive.adapter.inbound.rest

import com.timearchive.application.ListCurrentUserOwnedRanges
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me/owned-ranges")
class CurrentUserOwnedRangeController(
    private val listCurrentUserOwnedRanges: ListCurrentUserOwnedRanges,
    private val currentUserSession: CurrentUserSession,
) {
    @GetMapping
    fun list(request: HttpServletRequest): List<CurrentUserOwnedRangeResponse> {
        val currentUserId = currentUserSession.requireCurrentUserId(request)

        return listCurrentUserOwnedRanges
            .list(ListCurrentUserOwnedRanges.Query(currentUserId = currentUserId))
            .map(CurrentUserOwnedRangeResponse::from)
    }
}
