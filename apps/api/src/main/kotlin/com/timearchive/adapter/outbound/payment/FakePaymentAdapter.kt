package com.timearchive.adapter.outbound.payment

import com.timearchive.domain.model.CheckoutRequest
import com.timearchive.domain.model.CheckoutSession
import com.timearchive.domain.port.PaymentPort
import org.springframework.stereotype.Component

@Component
class FakePaymentAdapter : PaymentPort {
    override fun createCheckout(request: CheckoutRequest): CheckoutSession =
        CheckoutSession(
            provider = "fake",
            providerReference = "fake_checkout_${request.reservationId}",
            checkoutUrl = "https://payments.example.test/checkout/${request.reservationId}",
        )
}
