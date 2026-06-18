package com.timearchive.domain.model

import java.time.Instant
import java.util.UUID

data class UserAccount(
    val id: UUID,
    val email: String,
    val normalizedEmail: String,
    val passwordHash: String,
    val displayName: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(email.isNotBlank()) { "email must not be blank" }
        require(normalizedEmail.isNotBlank()) { "normalized email must not be blank" }
        require(passwordHash.isNotBlank()) { "password hash must not be blank" }
        require(displayName.isNotBlank()) { "display name must not be blank" }
    }

    companion object {
        fun create(
            id: UUID,
            email: String,
            passwordHash: String,
            displayName: String,
            now: Instant,
        ): UserAccount =
            UserAccount(
                id = id,
                email = email.trim(),
                normalizedEmail = normalizeEmail(email),
                passwordHash = passwordHash,
                displayName = displayName.trim(),
                createdAt = now,
                updatedAt = now,
            )

        fun normalizeEmail(email: String): String = email.trim().lowercase()
    }
}
