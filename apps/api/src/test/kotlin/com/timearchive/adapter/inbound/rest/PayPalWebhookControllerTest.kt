package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CompletePayPalWebhook
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class PayPalWebhookControllerTest {
    private val completePayPalWebhook: CompletePayPalWebhook = mockk()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(PayPalWebhookController(completePayPalWebhook))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `receives paypal webhook with verification headers`() {
        every { completePayPalWebhook.complete(any()) } returns CompletePayPalWebhook.Result(
            eventType = "PAYMENT.CAPTURE.COMPLETED",
            status = "COMPLETED",
            purchaseId = UUID.fromString("00000000-0000-0000-0000-000000000201"),
            ownershipRecordId = UUID.fromString("00000000-0000-0000-0000-000000000301"),
            alreadyProcessed = false,
        )
        val body = """{"id":"WH-1","event_type":"PAYMENT.CAPTURE.COMPLETED"}"""

        mockMvc.post("/api/payments/paypal/webhooks") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            header("PAYPAL-TRANSMISSION-ID", "transmission-1")
            header("PAYPAL-TRANSMISSION-TIME", "2026-07-03T00:00:00Z")
            header("PAYPAL-CERT-URL", "https://api-m.sandbox.paypal.com/certs/cert.pem")
            header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
            header("PAYPAL-TRANSMISSION-SIG", "signature")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.eventType") { value("PAYMENT.CAPTURE.COMPLETED") }
                jsonPath("$.status") { value("COMPLETED") }
                jsonPath("$.alreadyProcessed") { value(false) }
            }

        verify {
            completePayPalWebhook.complete(
                withArg {
                    kotlin.test.assertEquals(body, it.rawBody)
                    kotlin.test.assertEquals("transmission-1", it.headers.transmissionId)
                    kotlin.test.assertEquals("signature", it.headers.transmissionSig)
                },
            )
        }
    }

    @Test
    fun `rejects missing paypal verification header`() {
        mockMvc.post("/api/payments/paypal/webhooks") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"WH-1"}"""
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("PAYPAL_WEBHOOK_INVALID") }
            }
    }

    @Test
    fun `maps invalid paypal webhook failure`() {
        every {
            completePayPalWebhook.complete(any())
        } throws IllegalArgumentException("paypal webhook signature verification failed")

        postWebhook()
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("PAYPAL_WEBHOOK_INVALID") }
            }
    }

    @Test
    fun `maps paypal webhook local state mismatch`() {
        every {
            completePayPalWebhook.complete(any())
        } throws IllegalArgumentException("paypal capture reference mismatch")

        postWebhook()
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("PAYPAL_WEBHOOK_STATE_MISMATCH") }
            }
    }

    private fun postWebhook() =
        mockMvc.post("/api/payments/paypal/webhooks") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"WH-1","event_type":"PAYMENT.CAPTURE.COMPLETED"}"""
            header("PAYPAL-TRANSMISSION-ID", "transmission-1")
            header("PAYPAL-TRANSMISSION-TIME", "2026-07-03T00:00:00Z")
            header("PAYPAL-CERT-URL", "https://api-m.sandbox.paypal.com/certs/cert.pem")
            header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
            header("PAYPAL-TRANSMISSION-SIG", "signature")
        }
}
