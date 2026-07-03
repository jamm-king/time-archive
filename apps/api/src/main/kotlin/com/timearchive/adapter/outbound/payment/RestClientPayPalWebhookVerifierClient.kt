package com.timearchive.adapter.outbound.payment

import com.fasterxml.jackson.annotation.JsonProperty
import com.timearchive.configuration.PayPalPaymentProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Component
@ConditionalOnProperty(prefix = "time-archive.payment.paypal", name = ["enabled"], havingValue = "true")
class RestClientPayPalWebhookVerifierClient(
    restClientBuilder: RestClient.Builder,
    private val properties: PayPalPaymentProperties,
) : PayPalWebhookVerifierClient {
    private val restClient: RestClient = restClientBuilder
        .baseUrl(properties.apiBaseUrl.trimEnd('/'))
        .build()

    init {
        require(properties.apiBaseUrl.isNotBlank()) { "paypal api base url must not be blank" }
        require(properties.clientId.isNotBlank()) { "paypal client id must not be blank" }
        require(properties.clientSecret.isNotBlank()) { "paypal client secret must not be blank" }
    }

    override fun verify(command: PayPalWebhookVerificationCommand): Boolean {
        val response = restClient.post()
            .uri("/v1/notifications/verify-webhook-signature")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${requestAccessToken()}")
            .body(
                PayPalVerifyWebhookSignatureRequest(
                    authAlgo = command.authAlgo,
                    certUrl = command.certUrl,
                    transmissionId = command.transmissionId,
                    transmissionSig = command.transmissionSig,
                    transmissionTime = command.transmissionTime,
                    webhookId = command.webhookId,
                    webhookEvent = command.webhookEvent,
                ),
            )
            .retrieve()
            .body(PayPalVerifyWebhookSignatureResponse::class.java)
            ?: error("paypal webhook verification response was empty")

        return response.verificationStatus == "SUCCESS"
    }

    private fun requestAccessToken(): String {
        val form = LinkedMultiValueMap<String, String>()
        form.add("grant_type", "client_credentials")

        val response = restClient.post()
            .uri("/v1/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .headers { it.setBasicAuth(properties.clientId, properties.clientSecret) }
            .body(form)
            .retrieve()
            .body(PayPalAccessTokenResponse::class.java)
            ?: error("paypal access token response was empty")

        return response.accessToken ?: error("paypal access token response did not include an access token")
    }

    data class PayPalAccessTokenResponse(
        @JsonProperty("access_token")
        val accessToken: String? = null,
    )

    data class PayPalVerifyWebhookSignatureRequest(
        @JsonProperty("auth_algo")
        val authAlgo: String,
        @JsonProperty("cert_url")
        val certUrl: String,
        @JsonProperty("transmission_id")
        val transmissionId: String,
        @JsonProperty("transmission_sig")
        val transmissionSig: String,
        @JsonProperty("transmission_time")
        val transmissionTime: String,
        @JsonProperty("webhook_id")
        val webhookId: String,
        @JsonProperty("webhook_event")
        val webhookEvent: Any,
    )

    data class PayPalVerifyWebhookSignatureResponse(
        @JsonProperty("verification_status")
        val verificationStatus: String? = null,
    )
}
