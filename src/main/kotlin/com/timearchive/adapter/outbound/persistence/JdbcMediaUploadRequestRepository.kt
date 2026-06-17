package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.MediaUploadRequest
import com.timearchive.domain.model.MediaUploadRequestStatus
import com.timearchive.domain.port.MediaUploadRequestRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcMediaUploadRequestRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : MediaUploadRequestRepository {
    override fun save(request: MediaUploadRequest): MediaUploadRequest {
        val parameters = MapSqlParameterSource()
            .addValue("id", request.id)
            .addValue("ownershipRecordId", request.ownershipRecordId)
            .addValue("ownerId", request.ownerId)
            .addValue("mediaType", request.mediaType.name)
            .addValue("originalFilename", request.originalFilename)
            .addValue("contentType", request.contentType)
            .addValue("contentLengthBytes", request.contentLengthBytes)
            .addValue("objectKey", request.objectKey)
            .addValue("originalFileUrl", request.originalFileUrl)
            .addValue("status", request.status.name)
            .addValue("mediaAssetId", request.mediaAssetId)
            .addValue("expiresAt", Timestamp.from(request.expiresAt))
            .addValue("createdAt", Timestamp.from(request.createdAt))
            .addValue("updatedAt", Timestamp.from(request.updatedAt))

        jdbcTemplate.update(
            """
            insert into media_upload_requests (
                id,
                ownership_record_id,
                owner_id,
                media_type,
                original_filename,
                content_type,
                content_length_bytes,
                object_key,
                original_file_url,
                status,
                media_asset_id,
                expires_at,
                created_at,
                updated_at
            ) values (
                :id,
                :ownershipRecordId,
                :ownerId,
                :mediaType,
                :originalFilename,
                :contentType,
                :contentLengthBytes,
                :objectKey,
                :originalFileUrl,
                :status,
                :mediaAssetId,
                :expiresAt,
                :createdAt,
                :updatedAt
            )
            """.trimIndent(),
            parameters,
        )

        return request
    }

    override fun findById(id: UUID): MediaUploadRequest? {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)

        return jdbcTemplate.query(
            """
            $selectSql
            where id = :id
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toMediaUploadRequest() }.firstOrNull()
    }

    override fun findByIdForUpdate(id: UUID): MediaUploadRequest? {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)

        return jdbcTemplate.query(
            """
            $selectSql
            where id = :id
            for update
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toMediaUploadRequest() }.firstOrNull()
    }

    override fun findByOwnershipRecordId(ownershipRecordId: UUID): List<MediaUploadRequest> {
        val parameters = MapSqlParameterSource()
            .addValue("ownershipRecordId", ownershipRecordId)

        return jdbcTemplate.query(
            """
            $selectSql
            where ownership_record_id = :ownershipRecordId
            order by created_at desc, id
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toMediaUploadRequest() }
    }

    override fun markCompleted(
        id: UUID,
        mediaAssetId: UUID,
        now: java.time.Instant,
    ): Int {
        val parameters = MapSqlParameterSource()
            .addValue("id", id)
            .addValue("mediaAssetId", mediaAssetId)
            .addValue("updatedAt", Timestamp.from(now))

        return jdbcTemplate.update(
            """
            update media_upload_requests
            set status = 'COMPLETED',
                media_asset_id = :mediaAssetId,
                updated_at = :updatedAt
            where id = :id
              and status = 'REQUESTED'
            """.trimIndent(),
            parameters,
        )
    }

    private fun ResultSet.toMediaUploadRequest(): MediaUploadRequest =
        MediaUploadRequest(
            id = getObject("id", UUID::class.java),
            ownershipRecordId = getObject("ownership_record_id", UUID::class.java),
            ownerId = getObject("owner_id", UUID::class.java),
            mediaType = MediaType.valueOf(getString("media_type")),
            originalFilename = getString("original_filename"),
            contentType = getString("content_type"),
            contentLengthBytes = getLong("content_length_bytes"),
            objectKey = getString("object_key"),
            originalFileUrl = getString("original_file_url"),
            status = MediaUploadRequestStatus.valueOf(getString("status")),
            mediaAssetId = getObject("media_asset_id", UUID::class.java),
            expiresAt = getTimestamp("expires_at").toInstant(),
            createdAt = getTimestamp("created_at").toInstant(),
            updatedAt = getTimestamp("updated_at").toInstant(),
        )

    private val selectSql: String =
        """
        select
            id,
            ownership_record_id,
            owner_id,
            media_type,
            original_filename,
            content_type,
            content_length_bytes,
            object_key,
            original_file_url,
            status,
            media_asset_id,
            expires_at,
            created_at,
            updated_at
        from media_upload_requests
        """.trimIndent()
}
