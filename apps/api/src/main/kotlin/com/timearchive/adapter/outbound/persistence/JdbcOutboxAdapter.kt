package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.OutboxEvent
import com.timearchive.domain.port.OutboxPort
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.sql.Types

@Repository
class JdbcOutboxAdapter(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : OutboxPort {
    override fun append(event: OutboxEvent): OutboxEvent {
        val parameters = MapSqlParameterSource()
            .addValue("id", event.id)
            .addValue("eventType", event.eventType)
            .addValue("aggregateType", event.aggregateType)
            .addValue("aggregateId", event.aggregateId)
            .addValue("payload", event.payload)
            .addValue("status", event.status)
            .addValue("createdAt", Timestamp.from(event.createdAt), Types.TIMESTAMP)
            .addValue("processedAt", event.processedAt?.let { Timestamp.from(it) }, Types.TIMESTAMP)
            .addValue("retryCount", event.retryCount)
            .addValue("lastError", event.lastError)

        jdbcTemplate.update(
            """
            insert into outbox_events (
                id,
                event_type,
                aggregate_type,
                aggregate_id,
                payload,
                status,
                created_at,
                processed_at,
                retry_count,
                last_error
            ) values (
                :id,
                :eventType,
                :aggregateType,
                :aggregateId,
                cast(:payload as jsonb),
                :status,
                :createdAt,
                :processedAt,
                :retryCount,
                :lastError
            )
            """.trimIndent(),
            parameters,
        )

        return event
    }
}
