package com.timearchive.domain.port

import com.timearchive.domain.model.CheckoutRequest
import com.timearchive.domain.model.CheckoutSession

interface PaymentPort {
    val provider: String

    fun createCheckout(request: CheckoutRequest): CheckoutSession
}
