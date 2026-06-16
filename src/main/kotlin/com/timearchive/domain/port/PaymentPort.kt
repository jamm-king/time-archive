package com.timearchive.domain.port

import com.timearchive.domain.model.CheckoutRequest
import com.timearchive.domain.model.CheckoutSession

interface PaymentPort {
    fun createCheckout(request: CheckoutRequest): CheckoutSession
}
