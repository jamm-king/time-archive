package com.timearchive.application

import com.timearchive.domain.model.UserAccount
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.PasswordHasherPort
import com.timearchive.domain.port.UserAccountRepository
import java.util.UUID

class RegisterUser(
    private val userAccountRepository: UserAccountRepository,
    private val passwordHasherPort: PasswordHasherPort,
    private val clockPort: ClockPort,
) {
    fun register(command: Command): UserAccount {
        require(command.email.isNotBlank()) { "email must not be blank" }
        require(command.password.length >= MIN_PASSWORD_LENGTH) { "password must be at least 8 characters" }
        require(command.displayName.isNotBlank()) { "display name must not be blank" }

        val normalizedEmail = UserAccount.normalizeEmail(command.email)
        require(userAccountRepository.findByNormalizedEmail(normalizedEmail) == null) {
            "user email already exists"
        }

        return userAccountRepository.save(
            UserAccount.create(
                id = UUID.randomUUID(),
                email = command.email,
                passwordHash = passwordHasherPort.hash(command.password),
                displayName = command.displayName,
                now = clockPort.now(),
            ),
        )
    }

    data class Command(
        val email: String,
        val password: String,
        val displayName: String,
    )

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}
