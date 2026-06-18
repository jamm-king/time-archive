package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.UserAccount
import com.timearchive.domain.port.UserAccountRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.util.UUID

@Repository
class JdbcUserAccountRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : UserAccountRepository {
    override fun save(user: UserAccount): UserAccount {
        val parameters = MapSqlParameterSource()
            .addValue("id", user.id)
            .addValue("email", user.email)
            .addValue("normalizedEmail", user.normalizedEmail)
            .addValue("passwordHash", user.passwordHash)
            .addValue("displayName", user.displayName)
            .addValue("createdAt", Timestamp.from(user.createdAt), Types.TIMESTAMP)
            .addValue("updatedAt", Timestamp.from(user.updatedAt), Types.TIMESTAMP)

        jdbcTemplate.update(
            """
            insert into users (
                id,
                email,
                normalized_email,
                password_hash,
                display_name,
                created_at,
                updated_at
            ) values (
                :id,
                :email,
                :normalizedEmail,
                :passwordHash,
                :displayName,
                :createdAt,
                :updatedAt
            )
            """.trimIndent(),
            parameters,
        )

        return user
    }

    override fun findById(id: UUID): UserAccount? {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)

        return jdbcTemplate.query(
            """
            $selectSql
            where id = :id
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toUserAccount() }.firstOrNull()
    }

    override fun findByNormalizedEmail(normalizedEmail: String): UserAccount? {
        val parameters = MapSqlParameterSource()
            .addValue("normalizedEmail", normalizedEmail)

        return jdbcTemplate.query(
            """
            $selectSql
            where normalized_email = :normalizedEmail
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toUserAccount() }.firstOrNull()
    }

    private fun ResultSet.toUserAccount(): UserAccount =
        UserAccount(
            id = getObject("id", UUID::class.java),
            email = getString("email"),
            normalizedEmail = getString("normalized_email"),
            passwordHash = getString("password_hash"),
            displayName = getString("display_name"),
            createdAt = getTimestamp("created_at").toInstant(),
            updatedAt = getTimestamp("updated_at").toInstant(),
        )

    private val selectSql: String =
        """
        select
            id,
            email,
            normalized_email,
            password_hash,
            display_name,
            created_at,
            updated_at
        from users
        """.trimIndent()
}
