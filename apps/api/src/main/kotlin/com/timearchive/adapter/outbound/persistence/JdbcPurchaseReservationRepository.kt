package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.PurchaseReservationRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Repository
class JdbcPurchaseReservationRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : PurchaseReservationRepository {
    override fun save(reservation: PurchaseReservation): PurchaseReservation {
        val parameters = MapSqlParameterSource()
            .addValue("id", reservation.id)
            .addValue("buyerId", reservation.buyerId)
            .addValue("startSecond", reservation.range.startSecond)
            .addValue("endSecond", reservation.range.endSecond)
            .addValue("amountCents", reservation.amountCents)
            .addValue("currency", reservation.currency)
            .addValue("status", reservation.status.name)
            .addValue("expiresAt", Timestamp.from(reservation.expiresAt), Types.TIMESTAMP)
            .addValue("createdAt", Timestamp.from(reservation.createdAt), Types.TIMESTAMP)
            .addValue("updatedAt", Timestamp.from(reservation.updatedAt), Types.TIMESTAMP)

        jdbcTemplate.update(
            """
            insert into purchase_reservations (
                id,
                buyer_id,
                start_second,
                end_second,
                amount_cents,
                currency,
                status,
                expires_at,
                created_at,
                updated_at
            ) values (
                :id,
                :buyerId,
                :startSecond,
                :endSecond,
                :amountCents,
                :currency,
                :status,
                :expiresAt,
                :createdAt,
                :updatedAt
            )
            """.trimIndent(),
            parameters,
        )

        return reservation
    }

    override fun findByIdForUpdate(id: UUID): PurchaseReservation? {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)

        return jdbcTemplate.query(
            """
            select
                id,
                buyer_id,
                start_second,
                end_second,
                amount_cents,
                currency,
                status,
                expires_at,
                created_at,
                updated_at
            from purchase_reservations
            where id = :id
            for update
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toPurchaseReservation() }.firstOrNull()
    }

    override fun findActiveOverlapping(range: TimeRange): List<PurchaseReservation> {
        val parameters = MapSqlParameterSource()
            .addValue("startSecond", range.startSecond)
            .addValue("endSecond", range.endSecond)

        return jdbcTemplate.query(
            """
            select
                id,
                buyer_id,
                start_second,
                end_second,
                amount_cents,
                currency,
                status,
                expires_at,
                created_at,
                updated_at
            from purchase_reservations
            where status in ('HELD', 'CHECKOUT_CREATED')
              and int8range(start_second, end_second, '[)') && int8range(:startSecond, :endSecond, '[)')
            order by start_second, end_second
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toPurchaseReservation() }
    }

    override fun expireOverdue(now: Instant): Int {
        val parameters = MapSqlParameterSource()
            .addValue("now", Timestamp.from(now), Types.TIMESTAMP)

        return jdbcTemplate.update(
            """
            update purchase_reservations
            set status = 'EXPIRED',
                updated_at = :now
            where status in ('HELD', 'CHECKOUT_CREATED')
              and expires_at <= :now
            """.trimIndent(),
            parameters,
        )
    }

    override fun markCheckoutCreated(id: UUID, now: Instant): Int {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)
            .addValue("now", Timestamp.from(now), Types.TIMESTAMP)

        return jdbcTemplate.update(
            """
            update purchase_reservations
            set status = 'CHECKOUT_CREATED',
                updated_at = :now
            where id = :id
              and status = 'HELD'
            """.trimIndent(),
            parameters,
        )
    }

    override fun markCompleted(id: UUID, now: Instant): Int {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)
            .addValue("now", Timestamp.from(now), Types.TIMESTAMP)

        return jdbcTemplate.update(
            """
            update purchase_reservations
            set status = 'COMPLETED',
                updated_at = :now
            where id = :id
              and status in ('HELD', 'CHECKOUT_CREATED')
            """.trimIndent(),
            parameters,
        )
    }

    private fun ResultSet.toPurchaseReservation(): PurchaseReservation =
        PurchaseReservation(
            id = getObject("id", UUID::class.java),
            buyerId = getObject("buyer_id", UUID::class.java),
            range = TimeRange(
                startSecond = getLong("start_second"),
                endSecond = getLong("end_second"),
            ),
            amountCents = getLong("amount_cents"),
            currency = getString("currency"),
            status = PurchaseReservationStatus.valueOf(getString("status")),
            expiresAt = getTimestamp("expires_at").toInstant(),
            createdAt = getTimestamp("created_at").toInstant(),
            updatedAt = getTimestamp("updated_at").toInstant(),
        )
}
