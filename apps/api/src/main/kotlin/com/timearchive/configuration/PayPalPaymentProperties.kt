package com.timearchive.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "time-archive.payment.paypal")
data class PayPalPaymentProperties(
    val enabled: Boolean = false,
    val apiBaseUrl: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val returnUrl: String = "",
    val cancelUrl: String = "",
    val webhookId: String = "",
)
