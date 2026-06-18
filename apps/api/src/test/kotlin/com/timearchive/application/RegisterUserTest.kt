package com.timearchive.application

import com.timearchive.domain.model.UserAccount
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.PasswordHasherPort
import com.timearchive.domain.port.UserAccountRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RegisterUserTest {
    private val repository = FakeUserAccountRepository()
    private val passwordHasher = FakePasswordHasher()
    private val useCase = RegisterUser(
        userAccountRepository = repository,
        passwordHasherPort = passwordHasher,
        clockPort = ClockPort { Instant.parse("2026-06-18T00:00:00Z") },
    )

    @Test
    fun `registers user with normalized email and hashed password`() {
        val user = useCase.register(
            RegisterUser.Command(
                email = " USER@example.COM ",
                password = "password123",
                displayName = " User ",
            ),
        )

        assertThat(user.email).isEqualTo("USER@example.COM")
        assertThat(user.normalizedEmail).isEqualTo("user@example.com")
        assertThat(user.passwordHash).isEqualTo("hashed:password123")
        assertThat(user.displayName).isEqualTo("User")
        assertThat(repository.findById(user.id)).isEqualTo(user)
    }

    @Test
    fun `rejects duplicate normalized email`() {
        useCase.register(
            RegisterUser.Command(
                email = "user@example.com",
                password = "password123",
                displayName = "User",
            ),
        )

        assertThatThrownBy {
            useCase.register(
                RegisterUser.Command(
                    email = " USER@example.com ",
                    password = "password456",
                    displayName = "Other",
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("user email already exists")
    }

    @Test
    fun `rejects short password`() {
        assertThatThrownBy {
            useCase.register(
                RegisterUser.Command(
                    email = "user@example.com",
                    password = "short",
                    displayName = "User",
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("password must be at least 8 characters")
    }

    private class FakeUserAccountRepository : UserAccountRepository {
        private val users = mutableMapOf<UUID, UserAccount>()

        override fun save(user: UserAccount): UserAccount {
            users[user.id] = user
            return user
        }

        override fun findById(id: UUID): UserAccount? = users[id]

        override fun findByNormalizedEmail(normalizedEmail: String): UserAccount? =
            users.values.firstOrNull { it.normalizedEmail == normalizedEmail }
    }

    private class FakePasswordHasher : PasswordHasherPort {
        override fun hash(rawPassword: String): String = "hashed:$rawPassword"

        override fun matches(rawPassword: String, passwordHash: String): Boolean =
            passwordHash == hash(rawPassword)
    }
}
