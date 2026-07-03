package com.timearchive.adapter.outbound.payment

import com.timearchive.domain.model.CheckoutRequest
import com.timearchive.domain.model.CheckoutSession
import com.timearchive.domain.port.PaymentPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
@ConditionalOnProperty(
    prefix = "time-archive.payment.fake",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true,
)
@ConditionalOnProperty(prefix = "time-archive.payment.paypal", name = ["enabled"], havingValue = "true")
class PayPalPaymentAdapter(
    private val paypalOrderClient: PayPalOrderClient,
    private val properties: com.timearchive.configuration.PayPalPaymentProperties,
) : PaymentPort {
    override val provider: String = "paypal"

    init {
        require(properties.apiBaseUrl.isNotBlank()) { "paypal api base url must not be blank" }
        require(properties.clientId.isNotBlank()) { "paypal client id must not be blank" }
        require(properties.clientSecret.isNotBlank()) { "paypal client secret must not be blank" }
        require(properties.returnUrl.isNotBlank()) { "paypal return url must not be blank" }
        require(properties.cancelUrl.isNotBlank()) { "paypal cancel url must not be blank" }
    }

    override fun createCheckout(request: CheckoutRequest): CheckoutSession {
        val order = paypalOrderClient.createOrder(
            PayPalCreateOrderCommand(
                providerRequestId = request.providerRequestId,
                reservationId = request.reservationId.toString(),
                amountValue = formatAmount(request.amountCents),
                currency = request.currency,
                returnUrl = properties.returnUrl,
                cancelUrl = properties.cancelUrl,
            ),
        )

        return CheckoutSession(
            provider = provider,
            providerReference = order.orderId,
            checkoutUrl = order.approvalUrl,
        )
    }

    private fun formatAmount(amountCents: Long): String =
        BigDecimal.valueOf(amountCents, 2)
            .setScale(2, RoundingMode.UNNECESSARY)
            .toPlainString()
}
