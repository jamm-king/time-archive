package com.timearchive.adapter.inbound.rest

import com.timearchive.domain.model.UserAccount
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class RegisterRequest(
    @field:NotBlank
    @field:Email
    val email: String?,
    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val password: String?,
    @field:NotBlank
    @field:Size(max = 80)
    val displayName: String?,
)

data class LoginRequest(
    @field:NotBlank
    @field:Email
    val email: String?,
    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val password: String?,
)

data class CurrentUserResponse(
    val userId: UUID,
    val email: String,
    val displayName: String,
    val role: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: UserAccount): CurrentUserResponse =
            CurrentUserResponse(
                userId = user.id,
                email = user.email,
                displayName = user.displayName,
                role = user.role.name,
                createdAt = user.createdAt,
            )
    }
}
