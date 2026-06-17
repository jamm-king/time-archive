package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.Purchase
import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.PurchaseStatus
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.PurchaseRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.util.UUID

@Repository
class JdbcPurchaseRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : PurchaseRepository {
    override fun save(purchase: Purchase): Purchase {
        val parameters = MapSqlParameterSource()
            .addValue("id", purchase.id)
            .addValue("buyerId", purchase.buyerId)
            .addValue("reservationId", purchase.reservationId)
            .addValue("startSecond", purchase.range.startSecond)
            .addValue("endSecond", purchase.range.endSecond)
            .addValue("amountCents", purchase.amountCents)
            .addValue("currency", purchase.currency)
            .addValue("status", purchase.status.name)
            .addValue("paymentProvider", purchase.paymentProvider)
            .addValue("paymentReference", purchase.paymentReference)
            .addValue("createdAt", Timestamp.from(purchase.createdAt), Types.TIMESTAMP)
            .addValue("updatedAt", Timestamp.from(purchase.updatedAt), Types.TIMESTAMP)

        jdbcTemplate.update(
            """
            insert into purchases (
                id,
                buyer_id,
                reservation_id,
                start_second,
                end_second,
                amount_cents,
                currency,
                status,
                payment_provider,
                payment_reference,
                created_at,
                updated_at
            ) values (
                :id,
                :buyerId,
                :reservationId,
                :startSecond,
                :endSecond,
                :amountCents,
                :currency,
                :status,
                :paymentProvider,
                :paymentReference,
                :createdAt,
                :updatedAt
            )
            """.trimIndent(),
            parameters,
        )

        return purchase
    }

    override fun findByReservationId(reservationId: UUID): Purchase? {
        val parameters = MapSqlParameterSource()
            .addValue("reservationId", reservationId)

        return jdbcTemplate.query(
            """
            select
                id,
                buyer_id,
                reservation_id,
                start_second,
                end_second,
                amount_cents,
                currency,
                status,
                payment_provider,
                payment_reference,
                created_at,
                updated_at
            from purchases
            where reservation_id = :reservationId
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toPurchase() }.firstOrNull()
    }

    private fun ResultSet.toPurchase(): Purchase =
        Purchase(
            id = getObject("id", UUID::class.java),
            buyerId = getObject("buyer_id", UUID::class.java),
            reservationId = getObject("reservation_id", UUID::class.java),
            range = TimeRange(
                startSecond = getLong("start_second"),
                endSecond = getLong("end_second"),
            ),
            amountCents = getLong("amount_cents"),
            currency = getString("currency"),
            status = PurchaseStatus.valueOf(getString("status")),
            paymentProvider = getString("payment_provider"),
            paymentReference = getString("payment_reference"),
            createdAt = getTimestamp("created_at").toInstant(),
            updatedAt = getTimestamp("updated_at").toInstant(),
        )
}
