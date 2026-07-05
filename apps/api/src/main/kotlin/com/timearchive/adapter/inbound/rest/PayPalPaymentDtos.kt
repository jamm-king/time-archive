package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CapturePayPalOrder
import com.timearchive.application.GetPayPalOrderConfirmationStatus
import java.util.UUID

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

data class PayPalOrderConfirmationStatusResponse(
    val orderId: String,
    val reservationId: UUID,
    val purchaseId: UUID?,
    val ownershipRecordId: UUID?,
    val status: String,
    val terminal: Boolean,
) {
    companion object {
        fun from(result: GetPayPalOrderConfirmationStatus.Result): PayPalOrderConfirmationStatusResponse =
            PayPalOrderConfirmationStatusResponse(
                orderId = result.orderId,
                reservationId = result.reservationId,
                purchaseId = result.purchaseId,
                ownershipRecordId = result.ownershipRecordId,
                status = result.status.name,
                terminal = result.terminal,
            )
    }
}
