package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.PaymentEvent
import com.timearchive.domain.model.PaymentEventStatus
import com.timearchive.domain.port.PaymentEventRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Repository
class JdbcPaymentEventRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : PaymentEventRepository {
    override fun save(event: PaymentEvent): PaymentEvent {
        val parameters = MapSqlParameterSource()
            .addValue("id", event.id)
            .addValue("provider", event.provider)
            .addValue("providerEventId", event.providerEventId)
            .addValue("eventType", event.eventType)
            .addValue("payloadHash", event.payloadHash)
            .addValue("processingStatus", event.processingStatus.name)
            .addValue("receivedAt", Timestamp.from(event.receivedAt), Types.TIMESTAMP)
            .addValue("processedAt", event.processedAt?.let { Timestamp.from(it) }, Types.TIMESTAMP)

        jdbcTemplate.update(
            """
            insert into payment_events (
                id,
                provider,
                provider_event_id,
                event_type,
                payload_hash,
                processing_status,
                received_at,
                processed_at
            ) values (
                :id,
                :provider,
                :providerEventId,
                :eventType,
                :payloadHash,
                :processingStatus,
                :receivedAt,
                :processedAt
            )
            """.trimIndent(),
            parameters,
        )

        return event
    }

    override fun findByProviderAndEventId(
        provider: String,
        providerEventId: String,
    ): PaymentEvent? {
        val parameters = MapSqlParameterSource()
            .addValue("provider", provider)
            .addValue("providerEventId", providerEventId)

        return jdbcTemplate.query(
            """
            select
                id,
                provider,
                provider_event_id,
                event_type,
                payload_hash,
                processing_status,
                received_at,
                processed_at
            from payment_events
            where provider = :provider
              and provider_event_id = :providerEventId
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toPaymentEvent() }.firstOrNull()
    }

    override fun markProcessed(
        provider: String,
        providerEventId: String,
        processedAt: Instant,
    ): Int {
        val parameters = MapSqlParameterSource()
            .addValue("provider", provider)
            .addValue("providerEventId", providerEventId)
            .addValue("processedAt", Timestamp.from(processedAt), Types.TIMESTAMP)

        return jdbcTemplate.update(
            """
            update payment_events
            set processing_status = 'PROCESSED',
                processed_at = :processedAt
            where provider = :provider
              and provider_event_id = :providerEventId
              and processing_status <> 'PROCESSED'
            """.trimIndent(),
            parameters,
        )
    }

    private fun ResultSet.toPaymentEvent(): PaymentEvent =
        PaymentEvent(
            id = getObject("id", UUID::class.java),
            provider = getString("provider"),
            providerEventId = getString("provider_event_id"),
            eventType = getString("event_type"),
            payloadHash = getString("payload_hash"),
            processingStatus = PaymentEventStatus.valueOf(getString("processing_status")),
            receivedAt = getTimestamp("received_at").toInstant(),
            processedAt = getTimestamp("processed_at")?.toInstant(),
        )
}
