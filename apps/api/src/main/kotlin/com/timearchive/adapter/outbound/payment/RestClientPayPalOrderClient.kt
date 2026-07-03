package com.timearchive.adapter.outbound.payment

import com.timearchive.configuration.PayPalPaymentProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Component
@ConditionalOnProperty(prefix = "time-archive.payment.paypal", name = ["enabled"], havingValue = "true")
class RestClientPayPalOrderClient(
    restClientBuilder: RestClient.Builder,
    private val properties: PayPalPaymentProperties,
) : PayPalOrderClient {
    private val restClient: RestClient = restClientBuilder
        .baseUrl(properties.apiBaseUrl.trimEnd('/'))
        .build()

    override fun createOrder(command: PayPalCreateOrderCommand): PayPalOrderResult {
        val accessToken = requestAccessToken()
        val response = restClient.post()
            .uri("/v2/checkout/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .header("PayPal-Request-Id", command.providerRequestId)
            .body(command.toPayPalOrderRequest())
            .retrieve()
            .body(PayPalOrderResponse::class.java)
            ?: error("paypal create order returned an empty response")

        return PayPalOrderResult(
            orderId = response.id ?: error("paypal create order response did not include order id"),
            approvalUrl = response.approvalUrl()
                ?: error("paypal create order response did not include approval url"),
        )
    }

    override fun captureOrder(command: PayPalCaptureOrderCommand): PayPalCaptureResult {
        val accessToken = requestAccessToken()
        val response = restClient.post()
            .uri("/v2/checkout/orders/{orderId}/capture", command.orderId)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .header("PayPal-Request-Id", command.providerRequestId)
            .body(emptyMap<String, String>())
            .retrieve()
            .body(PayPalCaptureOrderResponse::class.java)
            ?: error("paypal capture order returned an empty response")

        return PayPalCaptureResult(
            orderId = response.id ?: command.orderId,
            captureId = response.captureId()
                ?: error("paypal capture order response did not include capture id"),
            status = response.status ?: "UNKNOWN",
        )
    }

    private fun requestAccessToken(): String {
        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "client_credentials")

        val response = restClient.post()
            .uri("/v1/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .headers { it.setBasicAuth(properties.clientId, properties.clientSecret) }
            .body(body)
            .retrieve()
            .body(PayPalAccessTokenResponse::class.java)
            ?: error("paypal access token response was empty")

        return response.accessToken ?: error("paypal access token response did not include an access token")
    }

    private fun PayPalCreateOrderCommand.toPayPalOrderRequest(): PayPalOrderRequest =
        PayPalOrderRequest(
            intent = "CAPTURE",
            purchaseUnits = listOf(
                PayPalPurchaseUnit(
                    referenceId = reservationId,
                    customId = reservationId,
                    amount = PayPalAmount(
                        currencyCode = currency,
                        value = amountValue,
                    ),
                ),
            ),
            paymentSource = PayPalPaymentSource(
                paypal = PayPalPaymentSource.PayPal(
                    experienceContext = PayPalPaymentSource.ExperienceContext(
                        returnUrl = returnUrl,
                        cancelUrl = cancelUrl,
                        userAction = "PAY_NOW",
                    ),
                ),
            ),
        )

    data class PayPalAccessTokenResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("access_token")
        val accessToken: String? = null,
    )

    data class PayPalOrderRequest(
        val intent: String,
        @com.fasterxml.jackson.annotation.JsonProperty("purchase_units")
        val purchaseUnits: List<PayPalPurchaseUnit>,
        @com.fasterxml.jackson.annotation.JsonProperty("payment_source")
        val paymentSource: PayPalPaymentSource,
    )

    data class PayPalPurchaseUnit(
        @com.fasterxml.jackson.annotation.JsonProperty("reference_id")
        val referenceId: String,
        @com.fasterxml.jackson.annotation.JsonProperty("custom_id")
        val customId: String,
        val amount: PayPalAmount,
    )

    data class PayPalAmount(
        @com.fasterxml.jackson.annotation.JsonProperty("currency_code")
        val currencyCode: String,
        val value: String,
    )

    data class PayPalPaymentSource(
        val paypal: PayPal,
    ) {
        data class PayPal(
            @com.fasterxml.jackson.annotation.JsonProperty("experience_context")
            val experienceContext: ExperienceContext,
        )

        data class ExperienceContext(
            @com.fasterxml.jackson.annotation.JsonProperty("return_url")
            val returnUrl: String,
            @com.fasterxml.jackson.annotation.JsonProperty("cancel_url")
            val cancelUrl: String,
            @com.fasterxml.jackson.annotation.JsonProperty("user_action")
            val userAction: String,
        )
    }

    data class PayPalOrderResponse(
        val id: String? = null,
        val links: List<PayPalLink> = emptyList(),
    ) {
        fun approvalUrl(): String? =
            links.firstOrNull { it.rel == "payer-action" }?.href
                ?: links.firstOrNull { it.rel == "approve" }?.href
    }

    data class PayPalLink(
        val href: String? = null,
        val rel: String? = null,
        val method: String? = null,
    )

    data class PayPalCaptureOrderResponse(
        val id: String? = null,
        val status: String? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("purchase_units")
        val purchaseUnits: List<PayPalCapturedPurchaseUnit> = emptyList(),
    ) {
        fun captureId(): String? =
            purchaseUnits
                .asSequence()
                .flatMap { it.payments?.captures.orEmpty().asSequence() }
                .firstOrNull { !it.id.isNullOrBlank() }
                ?.id
    }

    data class PayPalCapturedPurchaseUnit(
        val payments: PayPalCapturedPayments? = null,
    )

    data class PayPalCapturedPayments(
        val captures: List<PayPalCapture> = emptyList(),
    )

    data class PayPalCapture(
        val id: String? = null,
        val status: String? = null,
    )
}
