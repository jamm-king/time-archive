package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CapturePayPalOrder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments/paypal")
class PayPalPaymentController(
    private val capturePayPalOrder: CapturePayPalOrder,
    private val currentUserSession: CurrentUserSession,
) {
    @PostMapping("/orders/{orderId}/capture")
    fun captureOrder(
        @PathVariable orderId: String,
        httpRequest: HttpServletRequest,
    ): PayPalCaptureResponse {
        val result = capturePayPalOrder.capture(
            CapturePayPalOrder.Command(
                currentUserId = currentUserSession.requireCurrentUserId(httpRequest),
                orderId = orderId,
            ),
        )

        return PayPalCaptureResponse.from(result)
    }
}
