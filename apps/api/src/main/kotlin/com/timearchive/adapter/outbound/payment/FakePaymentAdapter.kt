package com.timearchive.adapter.outbound.payment

import com.timearchive.domain.model.CheckoutRequest
import com.timearchive.domain.model.CheckoutSession
import com.timearchive.domain.port.PaymentPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "time-archive.payment.fake", name = ["enabled"], havingValue = "true")
class FakePaymentAdapter : PaymentPort {
    override val provider: String = "fake"

    override fun createCheckout(request: CheckoutRequest): CheckoutSession =
        CheckoutSession(
            provider = provider,
            providerReference = "fake_checkout_${request.reservationId}",
            checkoutUrl = "https://payments.example.test/checkout/${request.reservationId}",
        )
}
