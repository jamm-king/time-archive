package com.timearchive.domain.port

import com.timearchive.domain.model.PaymentEvent
import java.time.Instant

interface PaymentEventRepository {
    fun save(event: PaymentEvent): PaymentEvent

    fun findByProviderAndEventId(provider: String, providerEventId: String): PaymentEvent?

    fun markProcessed(provider: String, providerEventId: String, processedAt: Instant): Int
}
