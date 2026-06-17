package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.PublicTimelineSegment
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.PublicTimelineSegmentRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class JdbcPublicTimelineSegmentRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : PublicTimelineSegmentRepository {
    override fun findApprovedOverlapping(range: TimeRange): List<PublicTimelineSegment> {
        val parameters = MapSqlParameterSource()
            .addValue("startSecond", range.startSecond)
            .addValue("endSecond", range.endSecond)

        return jdbcTemplate.query(
            """
            select
                greatest(o.start_second, :startSecond) as segment_start_second,
                least(o.end_second, :endSecond) as segment_end_second,
                m.id as media_asset_id,
                m.media_type,
                m.approved_file_url,
                m.thumbnail_url,
                m.external_link
            from ownership_records o
            join media_assets m on m.ownership_record_id = o.id
            where o.status = 'ACTIVE'
              and o.valid_until is null
              and m.moderation_status = 'APPROVED'
              and m.approved_file_url is not null
              and int8range(o.start_second, o.end_second, '[)') && int8range(:startSecond, :endSecond, '[)')
            order by o.start_second, o.end_second, m.created_at, m.id
            """.trimIndent(),
            parameters,
        ) { rs, _ -> rs.toPublicTimelineSegment() }
    }

    private fun ResultSet.toPublicTimelineSegment(): PublicTimelineSegment =
        PublicTimelineSegment(
            range = TimeRange(
                startSecond = getLong("segment_start_second"),
                endSecond = getLong("segment_end_second"),
            ),
            mediaAssetId = getObject("media_asset_id", UUID::class.java),
            mediaType = MediaType.valueOf(getString("media_type")),
            mediaUrl = getString("approved_file_url"),
            thumbnailUrl = getString("thumbnail_url"),
            externalLink = getString("external_link"),
        )
}
