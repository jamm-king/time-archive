package com.timearchive.adapter.inbound.rest

import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/csrf")
class CsrfController {
    @GetMapping
    fun csrf(csrfToken: CsrfToken): CsrfTokenResponse =
        CsrfTokenResponse(
            headerName = csrfToken.headerName,
            parameterName = csrfToken.parameterName,
            token = csrfToken.token,
        )
}

data class CsrfTokenResponse(
    val headerName: String,
    val parameterName: String,
    val token: String,
)
