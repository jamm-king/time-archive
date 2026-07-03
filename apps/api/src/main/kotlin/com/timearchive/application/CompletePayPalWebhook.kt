package com.timearchive.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.timearchive.adapter.outbound.payment.PayPalWebhookVerificationCommand
import com.timearchive.adapter.outbound.payment.PayPalWebhookVerifierClient
import com.timearchive.configuration.PayPalPaymentProperties
import com.timearchive.domain.model.CheckoutAttemptStatus
import com.timearchive.domain.port.CheckoutAttemptRepository
import com.timearchive.domain.port.PurchaseReservationRepository
import com.timearchive.domain.port.TransactionPort
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

class CompletePayPalWebhook(
    private val objectMapper: ObjectMapper,
    private val transactionPort: TransactionPort,
    private val checkoutAttemptRepository: CheckoutAttemptRepository,
    private val purchaseReservationRepository: PurchaseReservationRepository,
    private val paypalWebhookVerifierClient: PayPalWebhookVerifierClient,
    private val completePrimaryPurchase: CompletePrimaryPurchase,
    private val properties: PayPalPaymentProperties,
) {
    fun complete(command: Command): Result {
        require(properties.webhookId.isNotBlank()) { "paypal webhook id must not be blank" }
        val event = objectMapper.readTree(command.rawBody)
        verifySignature(command, event)

        val eventType = event.requiredText("event_type")
        if (eventType != PAYMENT_CAPTURE_COMPLETED) {
            return Result.ignored(eventType)
        }

        val completedCapture = event.toCompletedCapture()
        validateLocalRecords(completedCapture)

        val completion = completePrimaryPurchase.complete(
            CompletePrimaryPurchase.Command(
                provider = "paypal",
                providerEventId = event.requiredText("id"),
                eventType = eventType,
                payloadHash = sha256(command.rawBody),
                reservationId = completedCapture.reservationId,
                paymentReference = completedCapture.captureId,
                requestId = command.requestId,
                paymentCompletedAt = completedCapture.completedAt,
            ),
        )

        return Result(
            eventType = eventType,
            status = "COMPLETED",
            purchaseId = completion.purchaseId,
            ownershipRecordId = completion.ownershipRecordId,
            alreadyProcessed = completion.alreadyProcessed,
        )
    }

    private fun verifySignature(command: Command, event: JsonNode) {
        val verified = paypalWebhookVerifierClient.verify(
            PayPalWebhookVerificationCommand(
                transmissionId = command.headers.transmissionId,
                transmissionTime = command.headers.transmissionTime,
                certUrl = command.headers.certUrl,
                authAlgo = command.headers.authAlgo,
                transmissionSig = command.headers.transmissionSig,
                webhookId = properties.webhookId,
                webhookEvent = event,
            ),
        )
        require(verified) { "paypal webhook signature verification failed" }
    }

    private fun JsonNode.toCompletedCapture(): CompletedCapture {
        val resource = required("resource")
        val amount = resource.required("amount")
        val relatedIds = resource.path("supplementary_data").path("related_ids")

        return CompletedCapture(
            captureId = resource.requiredText("id"),
            status = resource.requiredText("status"),
            reservationId = UUID.fromString(resource.requiredText("custom_id")),
            amountCents = amount.requiredText("value").toCents(),
            currency = amount.requiredText("currency_code"),
            orderId = relatedIds.path("order_id").textValue(),
            completedAt = resource.optionalInstant("update_time")
                ?: resource.optionalInstant("create_time")
                ?: throw IllegalArgumentException("paypal capture completion time is required"),
        )
    }

    private fun validateLocalRecords(capture: CompletedCapture) {
        require(capture.status == "COMPLETED") { "paypal capture is not completed" }

        transactionPort.execute {
            val reservation = purchaseReservationRepository.findByIdForUpdate(capture.reservationId)
                ?: error("purchase reservation not found")
            val checkoutAttempt = checkoutAttemptRepository.findByReservationIdForUpdate(capture.reservationId)
                ?: error("checkout attempt not found")

            require(checkoutAttempt.provider == "paypal") { "paypal checkout attempt provider mismatch" }
            require(checkoutAttempt.status == CheckoutAttemptStatus.CAPTURED_PENDING_WEBHOOK) {
                "paypal checkout attempt is not captured"
            }
            require(checkoutAttempt.captureReference == capture.captureId) {
                "paypal capture reference mismatch"
            }
            if (!capture.orderId.isNullOrBlank()) {
                require(checkoutAttempt.providerReference == capture.orderId) {
                    "paypal order reference mismatch"
                }
            }
            require(reservation.amountCents == capture.amountCents) { "paypal capture amount mismatch" }
            require(reservation.currency == capture.currency) { "paypal capture currency mismatch" }
        }
    }

    data class Command(
        val rawBody: String,
        val headers: Headers,
        val requestId: String?,
    )

    data class Headers(
        val transmissionId: String,
        val transmissionTime: String,
        val certUrl: String,
        val authAlgo: String,
        val transmissionSig: String,
    )

    data class Result(
        val eventType: String,
        val status: String,
        val purchaseId: UUID?,
        val ownershipRecordId: UUID?,
        val alreadyProcessed: Boolean,
    ) {
        companion object {
            fun ignored(eventType: String): Result =
                Result(
                    eventType = eventType,
                    status = "IGNORED",
                    purchaseId = null,
                    ownershipRecordId = null,
                    alreadyProcessed = false,
                )
        }
    }

    private data class CompletedCapture(
        val captureId: String,
        val status: String,
        val reservationId: UUID,
        val amountCents: Long,
        val currency: String,
        val orderId: String?,
        val completedAt: Instant,
    )

    private fun JsonNode.required(fieldName: String): JsonNode =
        path(fieldName).takeIf { !it.isMissingNode && !it.isNull }
            ?: throw IllegalArgumentException("paypal webhook $fieldName is required")

    private fun JsonNode.requiredText(fieldName: String): String =
        required(fieldName).textValue()?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("paypal webhook $fieldName is required")

    private fun JsonNode.optionalInstant(fieldName: String): Instant? =
        path(fieldName).textValue()?.takeIf(String::isNotBlank)?.let(Instant::parse)

    private fun String.toCents(): Long =
        BigDecimal(this)
            .movePointRight(2)
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()

    private fun sha256(value: String): String =
        "sha256:" + HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)),
        )

    companion object {
        const val PAYMENT_CAPTURE_COMPLETED: String = "PAYMENT.CAPTURE.COMPLETED"
    }
}
