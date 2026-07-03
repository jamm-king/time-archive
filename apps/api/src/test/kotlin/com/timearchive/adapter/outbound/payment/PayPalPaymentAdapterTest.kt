package com.timearchive.adapter.outbound.payment

import com.timearchive.configuration.PayPalPaymentProperties
import com.timearchive.domain.model.CheckoutRequest
import com.timearchive.domain.model.TimeRange
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class PayPalPaymentAdapterTest {
    @Test
    fun `creates paypal order using server computed checkout data`() {
        val paypalOrderClient = FakePayPalOrderClient()
        val adapter = PayPalPaymentAdapter(
            paypalOrderClient = paypalOrderClient,
            properties = properties(),
        )
        val reservationId = UUID.randomUUID()
        val attemptId = UUID.randomUUID()
        val providerRequestId = UUID.randomUUID().toString()

        val checkout = adapter.createCheckout(
            CheckoutRequest(
                reservationId = reservationId,
                checkoutAttemptId = attemptId,
                providerRequestId = providerRequestId,
                buyerId = UUID.randomUUID(),
                range = TimeRange(startSecond = 10, endSecond = 15),
                amountCents = 500,
                currency = "USD",
            ),
        )

        assertThat(checkout.provider).isEqualTo("paypal")
        assertThat(checkout.providerReference).isEqualTo("paypal-order-1")
        assertThat(checkout.checkoutUrl).isEqualTo("https://www.sandbox.paypal.com/checkoutnow?token=paypal-order-1")

        val command = paypalOrderClient.commands.single()
        assertThat(command.providerRequestId).isEqualTo(providerRequestId)
        assertThat(command.reservationId).isEqualTo(reservationId.toString())
        assertThat(command.amountValue).isEqualTo("5.00")
        assertThat(command.currency).isEqualTo("USD")
        assertThat(command.returnUrl).isEqualTo("https://staging.time-archive.com/payments/paypal/return")
        assertThat(command.cancelUrl).isEqualTo("https://staging.time-archive.com/payments/paypal/cancel")
    }

    @Test
    fun `formats one second price as dollars and cents`() {
        val paypalOrderClient = FakePayPalOrderClient()
        val adapter = PayPalPaymentAdapter(
            paypalOrderClient = paypalOrderClient,
            properties = properties(),
        )

        adapter.createCheckout(
            CheckoutRequest(
                reservationId = UUID.randomUUID(),
                checkoutAttemptId = UUID.randomUUID(),
                providerRequestId = UUID.randomUUID().toString(),
                buyerId = UUID.randomUUID(),
                range = TimeRange(startSecond = 1, endSecond = 2),
                amountCents = 100,
                currency = "USD",
            ),
        )

        assertThat(paypalOrderClient.commands.single().amountValue).isEqualTo("1.00")
    }

    private fun properties(): PayPalPaymentProperties =
        PayPalPaymentProperties(
            enabled = true,
            apiBaseUrl = "https://api-m.sandbox.paypal.com",
            clientId = "client-id",
            clientSecret = "client-secret",
            returnUrl = "https://staging.time-archive.com/payments/paypal/return",
            cancelUrl = "https://staging.time-archive.com/payments/paypal/cancel",
        )

    private class FakePayPalOrderClient : PayPalOrderClient {
        val commands = mutableListOf<PayPalCreateOrderCommand>()

        override fun createOrder(command: PayPalCreateOrderCommand): PayPalOrderResult {
            commands.add(command)
            return PayPalOrderResult(
                orderId = "paypal-order-1",
                approvalUrl = "https://www.sandbox.paypal.com/checkoutnow?token=paypal-order-1",
            )
        }

        override fun captureOrder(command: PayPalCaptureOrderCommand): PayPalCaptureResult =
            error("not used")
    }
}
