package com.timearchive.application

import com.timearchive.domain.model.UserAccount
import com.timearchive.domain.port.PasswordHasherPort
import com.timearchive.domain.port.UserAccountRepository

class AuthenticateUser(
    private val userAccountRepository: UserAccountRepository,
    private val passwordHasherPort: PasswordHasherPort,
) {
    fun authenticate(command: Command): UserAccount {
        val user = userAccountRepository.findByNormalizedEmail(
            UserAccount.normalizeEmail(command.email),
        ) ?: throw IllegalArgumentException("invalid credentials")

        require(passwordHasherPort.matches(command.password, user.passwordHash)) {
            "invalid credentials"
        }

        return user
    }

    data class Command(
        val email: String,
        val password: String,
    )
}
