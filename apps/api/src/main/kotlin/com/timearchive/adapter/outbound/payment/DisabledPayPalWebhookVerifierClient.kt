package com.timearchive.adapter.outbound.payment

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "time-archive.payment.paypal",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true,
)
class DisabledPayPalWebhookVerifierClient : PayPalWebhookVerifierClient {
    override fun verify(command: PayPalWebhookVerificationCommand): Boolean =
        error("payment provider is unavailable")
}
