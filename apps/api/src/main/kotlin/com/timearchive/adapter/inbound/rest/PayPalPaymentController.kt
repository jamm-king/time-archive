package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CapturePayPalOrder
import com.timearchive.application.GetPayPalOrderConfirmationStatus
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments/paypal")
class PayPalPaymentController(
    private val capturePayPalOrder: CapturePayPalOrder,
    private val getPayPalOrderConfirmationStatus: GetPayPalOrderConfirmationStatus,
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

    @GetMapping("/orders/{orderId}/confirmation-status")
    fun getConfirmationStatus(
        @PathVariable orderId: String,
        httpRequest: HttpServletRequest,
    ): PayPalOrderConfirmationStatusResponse {
        val result = getPayPalOrderConfirmationStatus.get(
            GetPayPalOrderConfirmationStatus.Command(
                currentUserId = currentUserSession.requireCurrentUserId(httpRequest),
                orderId = orderId,
            ),
        )

        return PayPalOrderConfirmationStatusResponse.from(result)
    }
}
