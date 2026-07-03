package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CapturePayPalOrder

data class PayPalCaptureResponse(
    val orderId: String,
    val captureReference: String,
    val status: String,
    val alreadyCaptured: Boolean,
) {
    companion object {
        fun from(result: CapturePayPalOrder.Result): PayPalCaptureResponse =
            PayPalCaptureResponse(
                orderId = result.orderId,
                captureReference = result.captureReference,
                status = result.status,
                alreadyCaptured = result.alreadyCaptured,
            )
    }
}
