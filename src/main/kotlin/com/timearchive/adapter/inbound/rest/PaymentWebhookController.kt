package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CompletePrimaryPurchase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/payments/fake/webhooks")
class PaymentWebhookController(
    private val completePrimaryPurchase: CompletePrimaryPurchase,
) {
    @PostMapping("/primary-purchase-completed")
    fun completeFakePrimaryPurchasePayment(
        @Valid @RequestBody request: FakePrimaryPurchasePaymentWebhookRequest,
    ): PrimaryPurchasePaymentWebhookResponse {
        val result = completePrimaryPurchase.complete(
            CompletePrimaryPurchase.Command(
                provider = "fake",
                providerEventId = requireNotNull(request.providerEventId),
                eventType = requireNotNull(request.eventType),
                payloadHash = requireNotNull(request.payloadHash),
                reservationId = requireNotNull(request.reservationId),
                paymentReference = requireNotNull(request.paymentReference),
                requestId = request.requestId,
            ),
        )

        return PrimaryPurchasePaymentWebhookResponse.from(result)
    }
}
