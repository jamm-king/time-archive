package com.timearchive.adapter.inbound.rest

import com.timearchive.adapter.inbound.web.RequestCorrelationFilter
import com.timearchive.application.CompletePayPalWebhook
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments/paypal/webhooks")
class PayPalWebhookController(
    private val completePayPalWebhook: CompletePayPalWebhook,
) {
    @PostMapping
    fun receivePayPalWebhook(
        @RequestBody rawBody: String,
        @RequestHeader headers: HttpHeaders,
        request: HttpServletRequest,
    ): PayPalWebhookResponse {
        val result = completePayPalWebhook.complete(
            CompletePayPalWebhook.Command(
                rawBody = rawBody,
                headers = CompletePayPalWebhook.Headers(
                    transmissionId = requiredHeader(headers, "PAYPAL-TRANSMISSION-ID"),
                    transmissionTime = requiredHeader(headers, "PAYPAL-TRANSMISSION-TIME"),
                    certUrl = requiredHeader(headers, "PAYPAL-CERT-URL"),
                    authAlgo = requiredHeader(headers, "PAYPAL-AUTH-ALGO"),
                    transmissionSig = requiredHeader(headers, "PAYPAL-TRANSMISSION-SIG"),
                ),
                requestId = RequestCorrelationFilter.requestIdFrom(request),
            ),
        )

        return PayPalWebhookResponse.from(result)
    }

    private fun requiredHeader(headers: HttpHeaders, name: String): String =
        headers.getFirst(name)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("paypal webhook header is missing: $name")
}
