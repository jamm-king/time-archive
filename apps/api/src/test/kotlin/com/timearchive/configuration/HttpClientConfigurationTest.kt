package com.timearchive.configuration

import com.timearchive.adapter.outbound.payment.RestClientPayPalOrderClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient

class HttpClientConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PayPalClientTestConfiguration::class.java)

    @Test
    fun `creates rest client builder`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(RestClient.Builder::class.java)
        }
    }

    @Test
    fun `creates paypal rest client when paypal is enabled`() {
        contextRunner
            .withPropertyValues(
                "time-archive.payment.paypal.enabled=true",
                "time-archive.payment.paypal.api-base-url=https://api-m.sandbox.paypal.com",
                "time-archive.payment.paypal.client-id=client-id",
                "time-archive.payment.paypal.client-secret=client-secret",
                "time-archive.payment.paypal.return-url=https://staging.time-archive.com/payments/paypal/return",
                "time-archive.payment.paypal.cancel-url=https://staging.time-archive.com/payments/paypal/cancel",
            )
            .run { context ->
                assertThat(context).hasSingleBean(RestClientPayPalOrderClient::class.java)
            }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PayPalPaymentProperties::class)
    @Import(
        HttpClientConfiguration::class,
        RestClientPayPalOrderClient::class,
    )
    class PayPalClientTestConfiguration
}
