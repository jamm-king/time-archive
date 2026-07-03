package com.timearchive.domain.model

enum class CheckoutAttemptStatus {
    PENDING_PROVIDER,
    PROVIDER_CREATED,
    PROVIDER_FAILED,
    CAPTURED_PENDING_WEBHOOK,
    CAPTURE_FAILED,
    CANCELLED,
}
