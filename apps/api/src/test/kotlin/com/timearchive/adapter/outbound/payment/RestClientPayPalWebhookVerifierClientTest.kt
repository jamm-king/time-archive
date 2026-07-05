package com.timearchive.adapter.outbound.payment

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.timearchive.configuration.PayPalPaymentProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.http.HttpMethod

class RestClientPayPalWebhookVerifierClientTest {
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()

    @Test
    fun `returns true when paypal verification succeeds`() {
        val fixture = fixture()
        fixture.expectAccessToken()
        fixture.expectVerification("""{"verification_status":"SUCCESS"}""")

        val verified = fixture.client.verify(command())

        assertThat(verified).isTrue()
        fixture.server.verify()
    }

    @Test
    fun `returns false when paypal verification status is failure`() {
        val fixture = fixture()
        fixture.expectAccessToken()
        fixture.expectVerification("""{"verification_status":"FAILURE"}""")

        val verified = fixture.client.verify(command())

        assertThat(verified).isFalse()
        fixture.server.verify()
    }

    @Test
    fun `returns false when paypal verification request fails`() {
        val fixture = fixture()
        fixture.expectAccessToken()
        fixture.server.expect(once(), requestTo("https://api-m.sandbox.paypal.com/v1/notifications/verify-webhook-signature"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError())

        val verified = fixture.client.verify(command())

        assertThat(verified).isFalse()
        fixture.server.verify()
    }

    private fun fixture(): Fixture {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = RestClientPayPalWebhookVerifierClient(
            restClientBuilder = builder,
            objectMapper = objectMapper,
            properties = properties(),
        )
        return Fixture(client, server)
    }

    private fun Fixture.expectAccessToken() {
        server.expect(once(), requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ="))
            .andRespond(
                withSuccess(
                    """{"access_token":"access-token"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
    }

    private fun Fixture.expectVerification(responseBody: String) {
        server.expect(once(), requestTo("https://api-m.sandbox.paypal.com/v1/notifications/verify-webhook-signature"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andExpect(
                content().json(
                    """
                    {
                      "auth_algo": "SHA256withRSA",
                      "cert_url": "https://api-m.sandbox.paypal.com/certs/cert.pem",
                      "transmission_id": "transmission-1234567890",
                      "transmission_sig": "signature",
                      "transmission_time": "2026-07-05T00:00:00Z",
                      "webhook_id": "WEBHOOK-1",
                      "webhook_event": {
                        "id": "WH-1",
                        "event_type": "PAYMENT.CAPTURE.COMPLETED"
                      }
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON))
    }

    private fun command(): PayPalWebhookVerificationCommand =
        PayPalWebhookVerificationCommand(
            transmissionId = "transmission-1234567890",
            transmissionTime = "2026-07-05T00:00:00Z",
            certUrl = "https://api-m.sandbox.paypal.com/certs/cert.pem",
            authAlgo = "SHA256withRSA",
            transmissionSig = "signature",
            webhookId = "WEBHOOK-1",
            webhookEvent = objectMapper.readTree("""{"id":"WH-1","event_type":"PAYMENT.CAPTURE.COMPLETED"}"""),
        )

    private fun properties(): PayPalPaymentProperties =
        PayPalPaymentProperties(
            enabled = true,
            apiBaseUrl = "https://api-m.sandbox.paypal.com",
            clientId = "client-id",
            clientSecret = "client-secret",
            returnUrl = "https://staging.time-archive.com/payments/paypal/return",
            cancelUrl = "https://staging.time-archive.com/payments/paypal/cancel",
            webhookId = "WEBHOOK-1",
        )

    private data class Fixture(
        val client: RestClientPayPalWebhookVerifierClient,
        val server: MockRestServiceServer,
    )
}
