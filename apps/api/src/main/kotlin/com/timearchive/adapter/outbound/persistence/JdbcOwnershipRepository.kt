package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.OwnershipStatus
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.OwnershipRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Repository
class JdbcOwnershipRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : OwnershipRepository {
    override fun save(record: OwnershipRecord): OwnershipRecord {
        val parameters = MapSqlParameterSource()
            .addValue("id", record.id)
            .addValue("startSecond", record.range.startSecond)
            .addValue("endSecond", record.range.endSecond)
            .addValue("ownerId", record.ownerId)
            .addValue("status", record.status.name)
            .addValue("validFrom", Timestamp.from(record.validFrom), Types.TIMESTAMP)
            .addValue("validUntil", record.validUntil?.let { Timestamp.from(it) }, Types.TIMESTAMP)
            .addValue("acquisitionType", record.acquisitionType.name)
            .addValue("sourcePurchaseId", record.sourcePurchaseId, Types.OTHER)
            .addValue("sourceTransactionId", record.sourceTransactionId, Types.OTHER)
            .addValue("createdAt", Timestamp.from(record.createdAt), Types.TIMESTAMP)
            .addValue("updatedAt", Timestamp.from(record.updatedAt), Types.TIMESTAMP)

        jdbcTemplate.update(
            """
            insert into ownership_records (
                id,
                start_second,
                end_second,
                owner_id,
                status,
                valid_from,
                valid_until,
                acquisition_type,
                source_purchase_id,
                source_transaction_id,
                created_at,
                updated_at
            ) values (
                :id,
                :startSecond,
                :endSecond,
                :ownerId,
                :status,
                :validFrom,
                :validUntil,
                :acquisitionType,
                :sourcePurchaseId,
                :sourceTransactionId,
                :createdAt,
                :updatedAt
            )
            """.trimIndent(),
            parameters,
        )

        return record
    }

    override fun findById(id: UUID): OwnershipRecord? {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)

        return jdbcTemplate.query(
            """
            $selectSql
            where id = :id
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toOwnershipRecord() }.firstOrNull()
    }

    override fun findActiveByOwnerId(ownerId: UUID): List<OwnershipRecord> {
        val parameters = MapSqlParameterSource()
            .addValue("ownerId", ownerId)

        return jdbcTemplate.query(
            """
            $selectSql
            where owner_id = :ownerId
              and status = 'ACTIVE'
              and valid_until is null
            order by start_second, end_second
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toOwnershipRecord() }
    }

    override fun findActiveOverlapping(range: TimeRange): List<OwnershipRecord> {
        val parameters = MapSqlParameterSource()
            .addValue("startSecond", range.startSecond)
            .addValue("endSecond", range.endSecond)

        return jdbcTemplate.query(
            """
            $selectSql
            where status = 'ACTIVE'
              and valid_until is null
              and int8range(start_second, end_second, '[)') && int8range(:startSecond, :endSecond, '[)')
            order by start_second, end_second
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toOwnershipRecord() }
    }

    private fun ResultSet.toOwnershipRecord(): OwnershipRecord =
        OwnershipRecord(
            id = getObject("id", UUID::class.java),
            range = TimeRange(
                startSecond = getLong("start_second"),
                endSecond = getLong("end_second"),
            ),
            ownerId = getObject("owner_id", UUID::class.java),
            status = OwnershipStatus.valueOf(getString("status")),
            validFrom = getTimestamp("valid_from").toInstant(),
            validUntil = getTimestamp("valid_until")?.toInstant(),
            acquisitionType = AcquisitionType.valueOf(getString("acquisition_type")),
            sourcePurchaseId = getObject("source_purchase_id", UUID::class.java),
            sourceTransactionId = getObject("source_transaction_id", UUID::class.java),
            createdAt = getTimestamp("created_at").toInstant(),
            updatedAt = getTimestamp("updated_at").toInstant(),
        )

    private val selectSql: String =
        """
        select
            id,
            start_second,
            end_second,
            owner_id,
            status,
            valid_from,
            valid_until,
            acquisition_type,
            source_purchase_id,
            source_transaction_id,
            created_at,
            updated_at
        from ownership_records
        """.trimIndent()
}
