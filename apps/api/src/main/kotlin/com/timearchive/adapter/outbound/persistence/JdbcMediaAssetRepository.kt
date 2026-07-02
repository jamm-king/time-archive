package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.ModerationStatus
import com.timearchive.domain.port.MediaAssetRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.util.UUID

@Repository
class JdbcMediaAssetRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : MediaAssetRepository {
    override fun save(asset: MediaAsset): MediaAsset {
        val parameters = MapSqlParameterSource()
            .addValue("id", asset.id)
            .addValue("ownershipRecordId", asset.ownershipRecordId)
            .addValue("ownerId", asset.ownerId)
            .addValue("mediaType", asset.mediaType.name)
            .addValue("originalFileUrl", asset.originalFileUrl)
            .addValue("approvedFileUrl", asset.approvedFileUrl)
            .addValue("thumbnailUrl", asset.thumbnailUrl)
            .addValue("externalLink", asset.externalLink)
            .addValue("durationMs", asset.durationMs)
            .addValue("moderationStatus", asset.moderationStatus.name)
            .addValue("createdAt", Timestamp.from(asset.createdAt), Types.TIMESTAMP)
            .addValue("updatedAt", Timestamp.from(asset.updatedAt), Types.TIMESTAMP)

        jdbcTemplate.update(
            """
            insert into media_assets (
                id,
                ownership_record_id,
                owner_id,
                media_type,
                original_file_url,
                approved_file_url,
                thumbnail_url,
                external_link,
                duration_ms,
                moderation_status,
                created_at,
                updated_at
            ) values (
                :id,
                :ownershipRecordId,
                :ownerId,
                :mediaType,
                :originalFileUrl,
                :approvedFileUrl,
                :thumbnailUrl,
                :externalLink,
                :durationMs,
                :moderationStatus,
                :createdAt,
                :updatedAt
            )
            """.trimIndent(),
            parameters,
        )

        return asset
    }

    override fun update(asset: MediaAsset): MediaAsset {
        val parameters = MapSqlParameterSource()
            .addValue("id", asset.id)
            .addValue("approvedFileUrl", asset.approvedFileUrl)
            .addValue("thumbnailUrl", asset.thumbnailUrl)
            .addValue("externalLink", asset.externalLink)
            .addValue("moderationStatus", asset.moderationStatus.name)
            .addValue("updatedAt", Timestamp.from(asset.updatedAt), Types.TIMESTAMP)

        val updatedRows = jdbcTemplate.update(
            """
            update media_assets
            set approved_file_url = :approvedFileUrl,
                thumbnail_url = :thumbnailUrl,
                external_link = :externalLink,
                moderation_status = :moderationStatus,
                updated_at = :updatedAt
            where id = :id
            """.trimIndent(),
            parameters,
        )
        check(updatedRows == 1) { "media asset update failed" }

        return asset
    }

    override fun findById(id: UUID): MediaAsset? {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)

        return jdbcTemplate.query(
            "$selectSql where id = :id",
            parameters,
        ) { rs, _ -> rs.toMediaAsset() }.firstOrNull()
    }

    override fun findByIdForUpdate(id: UUID): MediaAsset? {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)

        return jdbcTemplate.query(
            """
            $selectSql
            where id = :id
            for update
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toMediaAsset() }.firstOrNull()
    }

    override fun findByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> {
        val parameters = MapSqlParameterSource()
            .addValue("ownershipRecordId", ownershipRecordId)

        return jdbcTemplate.query(
            """
            $selectSql
            where ownership_record_id = :ownershipRecordId
            order by created_at, id
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toMediaAsset() }
    }

    override fun findApprovedByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> {
        val parameters = MapSqlParameterSource()
            .addValue("ownershipRecordId", ownershipRecordId)

        return jdbcTemplate.query(
            """
            $selectSql
            where ownership_record_id = :ownershipRecordId
              and moderation_status = 'APPROVED'
            order by created_at, id
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toMediaAsset() }
    }

    override fun findByModerationStatus(status: ModerationStatus): List<MediaAsset> {
        val parameters = MapSqlParameterSource()
            .addValue("status", status.name)

        return jdbcTemplate.query(
            """
            $selectSql
            where moderation_status = :status
            order by created_at, id
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toMediaAsset() }
    }

    override fun findByOwnerId(ownerId: UUID): List<MediaAsset> {
        val parameters = MapSqlParameterSource()
            .addValue("ownerId", ownerId)

        return jdbcTemplate.query(
            """
            $selectSql
            where owner_id = :ownerId
            order by created_at, id
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toMediaAsset() }
    }

    private fun ResultSet.toMediaAsset(): MediaAsset =
        MediaAsset(
            id = getObject("id", UUID::class.java),
            ownershipRecordId = getObject("ownership_record_id", UUID::class.java),
            ownerId = getObject("owner_id", UUID::class.java),
            mediaType = MediaType.valueOf(getString("media_type")),
            originalFileUrl = getString("original_file_url"),
            approvedFileUrl = getString("approved_file_url"),
            thumbnailUrl = getString("thumbnail_url"),
            externalLink = getString("external_link"),
            durationMs = getLong("duration_ms").takeUnless { wasNull() },
            moderationStatus = ModerationStatus.valueOf(getString("moderation_status")),
            createdAt = getTimestamp("created_at").toInstant(),
            updatedAt = getTimestamp("updated_at").toInstant(),
        )

    private companion object {
        val selectSql: String =
            """
            select
                id,
                ownership_record_id,
                owner_id,
                media_type,
                original_file_url,
                approved_file_url,
                thumbnail_url,
                external_link,
                duration_ms,
                moderation_status,
                created_at,
                updated_at
            from media_assets
            """.trimIndent()
    }
}
