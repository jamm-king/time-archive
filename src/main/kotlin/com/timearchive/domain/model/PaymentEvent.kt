package com.timearchive.domain.model

import java.time.Instant
import java.util.UUID

data class PaymentEvent(
    val id: UUID,
    val provider: String,
    val providerEventId: String,
    val eventType: String,
    val payloadHash: String,
    val processingStatus: PaymentEventStatus,
    val receivedAt: Instant,
    val processedAt: Instant?,
) {
    init {
        require(provider.isNotBlank()) { "provider must not be blank" }
        require(providerEventId.isNotBlank()) { "providerEventId must not be blank" }
        require(eventType.isNotBlank()) { "eventType must not be blank" }
        require(payloadHash.isNotBlank()) { "payloadHash must not be blank" }
        require(processingStatus != PaymentEventStatus.PROCESSED || processedAt != null) {
            "processed payment event must have processedAt"
        }
    }

    val isProcessed: Boolean
        get() = processingStatus == PaymentEventStatus.PROCESSED

    companion object {
        fun received(
            id: UUID,
            provider: String,
            providerEventId: String,
            eventType: String,
            payloadHash: String,
            receivedAt: Instant,
        ): PaymentEvent =
            PaymentEvent(
                id = id,
                provider = provider,
                providerEventId = providerEventId,
                eventType = eventType,
                payloadHash = payloadHash,
                processingStatus = PaymentEventStatus.RECEIVED,
                receivedAt = receivedAt,
                processedAt = null,
            )
    }
}
