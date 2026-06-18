package com.timearchive.application

import com.timearchive.domain.model.UserAccount
import com.timearchive.domain.port.PasswordHasherPort
import com.timearchive.domain.port.UserAccountRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AuthenticateUserTest {
    private val user = UserAccount.create(
        id = UUID.randomUUID(),
        email = "user@example.com",
        passwordHash = "hashed:password123",
        displayName = "User",
        now = Instant.parse("2026-06-18T00:00:00Z"),
    )
    private val useCase = AuthenticateUser(
        userAccountRepository = FakeUserAccountRepository(user),
        passwordHasherPort = FakePasswordHasher(),
    )

    @Test
    fun `authenticates user by normalized email`() {
        val result = useCase.authenticate(
            AuthenticateUser.Command(
                email = " USER@example.COM ",
                password = "password123",
            ),
        )

        assertThat(result).isEqualTo(user)
    }

    @Test
    fun `rejects wrong password`() {
        assertThatThrownBy {
            useCase.authenticate(
                AuthenticateUser.Command(
                    email = "user@example.com",
                    password = "wrong-password",
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("invalid credentials")
    }

    @Test
    fun `rejects unknown email`() {
        assertThatThrownBy {
            useCase.authenticate(
                AuthenticateUser.Command(
                    email = "missing@example.com",
                    password = "password123",
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("invalid credentials")
    }

    private class FakeUserAccountRepository(
        private val user: UserAccount,
    ) : UserAccountRepository {
        override fun save(user: UserAccount): UserAccount = user

        override fun findById(id: UUID): UserAccount? =
            user.takeIf { it.id == id }

        override fun findByNormalizedEmail(normalizedEmail: String): UserAccount? =
            user.takeIf { it.normalizedEmail == normalizedEmail }
    }

    private class FakePasswordHasher : PasswordHasherPort {
        override fun hash(rawPassword: String): String = "hashed:$rawPassword"

        override fun matches(rawPassword: String, passwordHash: String): Boolean =
            passwordHash == hash(rawPassword)
    }
}
