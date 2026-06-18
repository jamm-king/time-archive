package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.UserAccount
import com.timearchive.domain.model.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
class JdbcUserAccountRepositoryIntegrationTest {
    @Autowired
    private lateinit var repository: JdbcUserAccountRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun deleteRecords() {
        jdbcTemplate.execute("delete from users")
    }

    @Test
    fun `saves and finds user by id`() {
        val user = userAccount(email = "User@example.com")

        repository.save(user)

        val result = repository.findById(user.id)

        assertThat(result).isEqualTo(user)
    }

    @Test
    fun `finds user by normalized email`() {
        val user = userAccount(email = "User@example.com")
        repository.save(user)

        val result = repository.findByNormalizedEmail("user@example.com")

        assertThat(result).isEqualTo(user)
    }

    @Test
    fun `saves and finds admin user role`() {
        val user = userAccount(email = "Admin@example.com", role = UserRole.ADMIN)

        repository.save(user)

        val result = repository.findById(user.id)

        assertThat(result?.role).isEqualTo(UserRole.ADMIN)
    }

    @Test
    fun `rejects duplicate normalized email`() {
        repository.save(userAccount(email = "User@example.com"))

        assertThatThrownBy {
            repository.save(userAccount(email = " user@example.COM "))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun userAccount(
        email: String,
        now: Instant = Instant.parse("2026-06-18T00:00:00Z"),
        role: UserRole = UserRole.USER,
    ): UserAccount =
        UserAccount.create(
            id = UUID.randomUUID(),
            email = email,
            passwordHash = "hashed",
            displayName = "User",
            now = now,
            role = role,
        )

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("time_archive")
            .withUsername("time_archive")
            .withPassword("time_archive")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
