package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CompletePrimaryPurchase
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class FakePrimaryPurchasePaymentWebhookRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val providerEventId: String?,
    @field:NotBlank
    @field:Size(max = 200)
    val eventType: String?,
    @field:NotBlank
    @field:Size(max = 200)
    val payloadHash: String?,
    @field:NotNull
    val reservationId: UUID?,
    @field:NotBlank
    @field:Size(max = 200)
    val paymentReference: String?,
    @field:Size(max = 200)
    val requestId: String?,
)

data class PrimaryPurchasePaymentWebhookResponse(
    val purchaseId: UUID,
    val ownershipRecordId: UUID?,
    val alreadyProcessed: Boolean,
) {
    companion object {
        fun from(result: CompletePrimaryPurchase.Result): PrimaryPurchasePaymentWebhookResponse =
            PrimaryPurchasePaymentWebhookResponse(
                purchaseId = result.purchaseId,
                ownershipRecordId = result.ownershipRecordId,
                alreadyProcessed = result.alreadyProcessed,
            )
    }
}
