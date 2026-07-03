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
class DisabledPayPalOrderClient : PayPalOrderClient {
    override fun createOrder(command: PayPalCreateOrderCommand): PayPalOrderResult =
        error("payment provider is unavailable")

    override fun captureOrder(command: PayPalCaptureOrderCommand): PayPalCaptureResult =
        error("payment provider is unavailable")
}
