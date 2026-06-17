package com.timearchive.domain.model

data class CheckoutSession(
    val provider: String,
    val providerReference: String,
    val checkoutUrl: String,
) {
    init {
        require(provider.isNotBlank()) { "provider must not be blank" }
        require(providerReference.isNotBlank()) { "providerReference must not be blank" }
        require(checkoutUrl.isNotBlank()) { "checkoutUrl must not be blank" }
    }
}
