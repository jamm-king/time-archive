package com.timearchive.adapter.inbound.rest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.web.csrf.DefaultCsrfToken

class CsrfControllerTest {
    private val controller = CsrfController()

    @Test
    fun `returns csrf token metadata`() {
        val response = controller.csrf(
            DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-token"),
        )

        assertThat(response.headerName).isEqualTo("X-XSRF-TOKEN")
        assertThat(response.parameterName).isEqualTo("_csrf")
        assertThat(response.token).isEqualTo("csrf-token")
    }
}
