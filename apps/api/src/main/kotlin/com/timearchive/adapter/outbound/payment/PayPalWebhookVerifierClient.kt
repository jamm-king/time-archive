package com.timearchive.adapter.outbound.payment

data class PayPalWebhookVerificationCommand(
    val transmissionId: String,
    val transmissionTime: String,
    val certUrl: String,
    val authAlgo: String,
    val transmissionSig: String,
    val webhookId: String,
    val webhookEvent: Any,
)

interface PayPalWebhookVerifierClient {
    fun verify(command: PayPalWebhookVerificationCommand): Boolean
}
