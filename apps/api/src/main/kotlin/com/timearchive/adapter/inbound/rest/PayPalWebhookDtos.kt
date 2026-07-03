package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CompletePayPalWebhook
import java.util.UUID

data class PayPalWebhookResponse(
    val eventType: String,
    val status: String,
    val purchaseId: UUID?,
    val ownershipRecordId: UUID?,
    val alreadyProcessed: Boolean,
) {
    companion object {
        fun from(result: CompletePayPalWebhook.Result): PayPalWebhookResponse =
            PayPalWebhookResponse(
                eventType = result.eventType,
                status = result.status,
                purchaseId = result.purchaseId,
                ownershipRecordId = result.ownershipRecordId,
                alreadyProcessed = result.alreadyProcessed,
            )
    }
}
