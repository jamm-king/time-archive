package com.timearchive.adapter.outbound.payment

import com.timearchive.domain.model.CheckoutRequest
import com.timearchive.domain.model.CheckoutSession
import com.timearchive.domain.port.PaymentPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "time-archive.payment.fake",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true,
)
class DisabledPaymentAdapter : PaymentPort {
    override fun createCheckout(request: CheckoutRequest): CheckoutSession =
        error("payment provider is unavailable")
}
