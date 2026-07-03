package com.timearchive.adapter.outbound.payment

data class PayPalCreateOrderCommand(
    val providerRequestId: String,
    val reservationId: String,
    val amountValue: String,
    val currency: String,
    val returnUrl: String,
    val cancelUrl: String,
)

data class PayPalOrderResult(
    val orderId: String,
    val approvalUrl: String,
)

data class PayPalCaptureOrderCommand(
    val providerRequestId: String,
    val orderId: String,
)

data class PayPalCaptureResult(
    val orderId: String,
    val captureId: String,
    val status: String,
)

interface PayPalOrderClient {
    fun createOrder(command: PayPalCreateOrderCommand): PayPalOrderResult

    fun captureOrder(command: PayPalCaptureOrderCommand): PayPalCaptureResult
}
