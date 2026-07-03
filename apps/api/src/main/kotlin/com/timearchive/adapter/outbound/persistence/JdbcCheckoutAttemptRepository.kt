package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.CheckoutAttempt
import com.timearchive.domain.model.CheckoutAttemptStatus
import com.timearchive.domain.port.CheckoutAttemptRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Repository
class JdbcCheckoutAttemptRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : CheckoutAttemptRepository {
    override fun save(attempt: CheckoutAttempt): CheckoutAttempt {
        val parameters = MapSqlParameterSource()
            .addValue("id", attempt.id)
            .addValue("reservationId", attempt.reservationId)
            .addValue("buyerId", attempt.buyerId)
            .addValue("provider", attempt.provider)
            .addValue("providerRequestId", attempt.providerRequestId)
            .addValue("providerReference", attempt.providerReference)
            .addValue("checkoutUrl", attempt.checkoutUrl)
            .addValue("status", attempt.status.name)
            .addValue("createdAt", Timestamp.from(attempt.createdAt), Types.TIMESTAMP)
            .addValue("updatedAt", Timestamp.from(attempt.updatedAt), Types.TIMESTAMP)

        jdbcTemplate.update(
            """
            insert into checkout_attempts (
                id,
                reservation_id,
                buyer_id,
                provider,
                provider_request_id,
                provider_reference,
                checkout_url,
                status,
                created_at,
                updated_at
            ) values (
                :id,
                :reservationId,
                :buyerId,
                :provider,
                :providerRequestId,
                :providerReference,
                :checkoutUrl,
                :status,
                :createdAt,
                :updatedAt
            )
            """.trimIndent(),
            parameters,
        )

        return attempt
    }

    override fun findByReservationIdForUpdate(reservationId: UUID): CheckoutAttempt? {
        val parameters = MapSqlParameterSource()
            .addValue("reservationId", reservationId)

        return jdbcTemplate.query(
            """
            select
                id,
                reservation_id,
                buyer_id,
                provider,
                provider_request_id,
                provider_reference,
                checkout_url,
                status,
                created_at,
                updated_at
            from checkout_attempts
            where reservation_id = :reservationId
            for update
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toCheckoutAttempt() }.firstOrNull()
    }

    override fun markProviderCreated(
        id: UUID,
        providerReference: String,
        checkoutUrl: String,
        now: Instant,
    ): Int {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)
            .addValue("providerReference", providerReference)
            .addValue("checkoutUrl", checkoutUrl)
            .addValue("now", Timestamp.from(now), Types.TIMESTAMP)

        return jdbcTemplate.update(
            """
            update checkout_attempts
            set status = 'PROVIDER_CREATED',
                provider_reference = :providerReference,
                checkout_url = :checkoutUrl,
                updated_at = :now
            where id = :id
              and status in ('PENDING_PROVIDER', 'PROVIDER_FAILED')
            """.trimIndent(),
            parameters,
        )
    }

    override fun markProviderFailed(
        id: UUID,
        now: Instant,
    ): Int {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)
            .addValue("now", Timestamp.from(now), Types.TIMESTAMP)

        return jdbcTemplate.update(
            """
            update checkout_attempts
            set status = 'PROVIDER_FAILED',
                updated_at = :now
            where id = :id
              and status in ('PENDING_PROVIDER', 'PROVIDER_FAILED')
            """.trimIndent(),
            parameters,
        )
    }

    private fun ResultSet.toCheckoutAttempt(): CheckoutAttempt =
        CheckoutAttempt(
            id = getObject("id", UUID::class.java),
            reservationId = getObject("reservation_id", UUID::class.java),
            buyerId = getObject("buyer_id", UUID::class.java),
            provider = getString("provider"),
            providerRequestId = getString("provider_request_id"),
            providerReference = getString("provider_reference"),
            checkoutUrl = getString("checkout_url"),
            status = CheckoutAttemptStatus.valueOf(getString("status")),
            createdAt = getTimestamp("created_at").toInstant(),
            updatedAt = getTimestamp("updated_at").toInstant(),
        )
}
