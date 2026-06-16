package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.AuditLog
import com.timearchive.domain.port.AuditLogPort
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.sql.Types

@Repository
class JdbcAuditLogAdapter(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : AuditLogPort {
    override fun append(log: AuditLog): AuditLog {
        val parameters = MapSqlParameterSource()
            .addValue("id", log.id)
            .addValue("actorUserId", log.actorUserId, Types.OTHER)
            .addValue("actorType", log.actorType)
            .addValue("action", log.action)
            .addValue("resourceType", log.resourceType)
            .addValue("resourceId", log.resourceId)
            .addValue("beforeState", log.beforeState)
            .addValue("afterState", log.afterState)
            .addValue("requestId", log.requestId)
            .addValue("createdAt", Timestamp.from(log.createdAt), Types.TIMESTAMP)

        jdbcTemplate.update(
            """
            insert into audit_logs (
                id,
                actor_user_id,
                actor_type,
                action,
                resource_type,
                resource_id,
                before_state,
                after_state,
                request_id,
                created_at
            ) values (
                :id,
                :actorUserId,
                :actorType,
                :action,
                :resourceType,
                :resourceId,
                cast(:beforeState as jsonb),
                cast(:afterState as jsonb),
                :requestId,
                :createdAt
            )
            """.trimIndent(),
            parameters,
        )

        return log
    }
}
