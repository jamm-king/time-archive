package com.timearchive.configuration

import com.timearchive.adapter.inbound.rest.PaymentWebhookController
import com.timearchive.adapter.outbound.payment.DisabledPaymentAdapter
import com.timearchive.adapter.outbound.payment.FakePaymentAdapter
import com.timearchive.adapter.outbound.payment.PayPalOrderClient
import com.timearchive.adapter.outbound.payment.PayPalOrderResult
import com.timearchive.adapter.outbound.payment.PayPalCreateOrderCommand
import com.timearchive.adapter.outbound.payment.PayPalPaymentAdapter
import com.timearchive.application.CompletePrimaryPurchase
import com.timearchive.domain.port.PaymentPort
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

class FakePaymentConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PaymentTestConfiguration::class.java)

    @Test
    fun `disables fake payment by default`() {
        contextRunner.run { context ->
            assertThat(context.getBeansOfType(PaymentWebhookController::class.java)).isEmpty()
            assertThat(context.getBeansOfType(FakePaymentAdapter::class.java)).isEmpty()
            assertThat(context.getBeansOfType(DisabledPaymentAdapter::class.java)).hasSize(1)
            assertThat(context.getBeansOfType(PaymentPort::class.java).values.single())
                .isInstanceOf(DisabledPaymentAdapter::class.java)
        }
    }

    @Test
    fun `enables fake payment only when explicitly configured`() {
        contextRunner
            .withPropertyValues("time-archive.payment.fake.enabled=true")
            .run { context ->
                assertThat(context.getBeansOfType(PaymentWebhookController::class.java)).hasSize(1)
                assertThat(context.getBeansOfType(FakePaymentAdapter::class.java)).hasSize(1)
                assertThat(context.getBeansOfType(DisabledPaymentAdapter::class.java)).isEmpty()
                assertThat(context.getBeansOfType(PaymentPort::class.java).values.single())
                    .isInstanceOf(FakePaymentAdapter::class.java)
            }
    }

    @Test
    fun `enables paypal payment only when explicitly configured`() {
        contextRunner
            .withPropertyValues(
                "time-archive.payment.paypal.enabled=true",
                "time-archive.payment.paypal.api-base-url=https://api-m.sandbox.paypal.com",
                "time-archive.payment.paypal.client-id=client-id",
                "time-archive.payment.paypal.client-secret=client-secret",
                "time-archive.payment.paypal.return-url=https://staging.time-archive.com/paypal/return",
                "time-archive.payment.paypal.cancel-url=https://staging.time-archive.com/paypal/cancel",
            )
            .run { context ->
                assertThat(context.getBeansOfType(PaymentWebhookController::class.java)).isEmpty()
                assertThat(context.getBeansOfType(FakePaymentAdapter::class.java)).isEmpty()
                assertThat(context.getBeansOfType(DisabledPaymentAdapter::class.java)).isEmpty()
                assertThat(context.getBeansOfType(PayPalPaymentAdapter::class.java)).hasSize(1)
                assertThat(context.getBeansOfType(PaymentPort::class.java).values.single())
                    .isInstanceOf(PayPalPaymentAdapter::class.java)
            }
    }

    @Test
    fun `fake payment takes precedence when fake and paypal are both enabled`() {
        contextRunner
            .withPropertyValues(
                "time-archive.payment.fake.enabled=true",
                "time-archive.payment.paypal.enabled=true",
                "time-archive.payment.paypal.api-base-url=https://api-m.sandbox.paypal.com",
                "time-archive.payment.paypal.client-id=client-id",
                "time-archive.payment.paypal.client-secret=client-secret",
                "time-archive.payment.paypal.return-url=https://staging.time-archive.com/paypal/return",
                "time-archive.payment.paypal.cancel-url=https://staging.time-archive.com/paypal/cancel",
            )
            .run { context ->
                assertThat(context.getBeansOfType(FakePaymentAdapter::class.java)).hasSize(1)
                assertThat(context.getBeansOfType(PayPalPaymentAdapter::class.java)).isEmpty()
                assertThat(context.getBeansOfType(DisabledPaymentAdapter::class.java)).isEmpty()
                assertThat(context.getBeansOfType(PaymentPort::class.java).values.single())
                    .isInstanceOf(FakePaymentAdapter::class.java)
            }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(
        PaymentWebhookController::class,
        FakePaymentAdapter::class,
        DisabledPaymentAdapter::class,
        PayPalPaymentAdapter::class,
        ConfigurationPropertiesConfiguration::class,
    )
    class PaymentTestConfiguration {
        @Bean
        fun completePrimaryPurchase(): CompletePrimaryPurchase = mockk()

        @Bean
        fun paypalOrderClient(): PayPalOrderClient =
            object : PayPalOrderClient {
                override fun createOrder(command: PayPalCreateOrderCommand): PayPalOrderResult =
                    error("not used")
            }
    }
}
