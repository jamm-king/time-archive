package com.timearchive.adapter.outbound.payment

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.timearchive.configuration.PayPalPaymentProperties
import java.net.URI
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.slf4j.LoggerFactory

@Component
@ConditionalOnProperty(prefix = "time-archive.payment.paypal", name = ["enabled"], havingValue = "true")
class RestClientPayPalWebhookVerifierClient(
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
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
        return try {
            val response = restClient.post()
                .uri("/v1/notifications/verify-webhook-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${requestAccessToken()}")
                .body(
                    objectMapper.writeValueAsString(
                        PayPalVerifyWebhookSignatureRequest(
                            authAlgo = command.authAlgo,
                            certUrl = command.certUrl,
                            transmissionId = command.transmissionId,
                            transmissionSig = command.transmissionSig,
                            transmissionTime = command.transmissionTime,
                            webhookId = command.webhookId,
                            webhookEvent = command.webhookEvent.requireJsonNode(),
                        ),
                    ),
                )
                .retrieve()
                .body(PayPalVerifyWebhookSignatureResponse::class.java)
                ?: error("paypal webhook verification response was empty")

            val verified = response.verificationStatus == "SUCCESS"
            logVerificationResult(command, response.verificationStatus, verified)
            verified
        } catch (exception: RestClientResponseException) {
            logVerificationHttpFailure(command, exception)
            false
        }
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
        val webhookEvent: JsonNode,
    )

    data class PayPalVerifyWebhookSignatureResponse(
        @JsonProperty("verification_status")
        val verificationStatus: String? = null,
    )

    private fun logVerificationResult(
        command: PayPalWebhookVerificationCommand,
        verificationStatus: String?,
        verified: Boolean,
    ) {
        val logMessage = "paypal webhook verification completed eventId={} eventType={} verified={} " +
            "verificationStatus={} authAlgo={} certHost={} transmissionId={}"
        if (verified) {
            logger.info(
                logMessage,
                command.webhookEvent.safeEventId(),
                command.webhookEvent.safeEventType(),
                verified,
                verificationStatus ?: "-",
                command.authAlgo,
                command.certUrl.safeHost(),
                command.transmissionId.mask(),
            )
        } else {
            logger.warn(
                logMessage,
                command.webhookEvent.safeEventId(),
                command.webhookEvent.safeEventType(),
                verified,
                verificationStatus ?: "-",
                command.authAlgo,
                command.certUrl.safeHost(),
                command.transmissionId.mask(),
            )
        }
    }

    private fun logVerificationHttpFailure(
        command: PayPalWebhookVerificationCommand,
        exception: RestClientResponseException,
    ) {
        logger.warn(
            "paypal webhook verification http failure eventId={} eventType={} httpStatus={} authAlgo={} certHost={} transmissionId={}",
            command.webhookEvent.safeEventId(),
            command.webhookEvent.safeEventType(),
            exception.statusCode.value(),
            command.authAlgo,
            command.certUrl.safeHost(),
            command.transmissionId.mask(),
        )
    }

    private fun Any.safeEventId(): String =
        asJsonNode()?.path("id")?.textValue()?.takeIf(String::isNotBlank) ?: "-"

    private fun Any.safeEventType(): String =
        asJsonNode()?.path("event_type")?.textValue()?.takeIf(String::isNotBlank) ?: "-"

    private fun Any.asJsonNode(): JsonNode? =
        this as? JsonNode

    private fun Any.requireJsonNode(): JsonNode =
        asJsonNode() ?: error("paypal webhook event must be a json node")

    private fun String.safeHost(): String =
        runCatching { URI(this).host }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: "-"

    private fun String.mask(): String =
        when {
            isBlank() -> "-"
            length <= 8 -> "*".repeat(length)
            else -> "${take(4)}...${takeLast(4)}"
        }

    companion object {
        private val logger = LoggerFactory.getLogger(RestClientPayPalWebhookVerifierClient::class.java)
    }
}
