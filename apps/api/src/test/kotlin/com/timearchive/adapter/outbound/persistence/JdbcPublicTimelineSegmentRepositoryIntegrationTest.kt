package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.ModerationStatus
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.TimeRange
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
class JdbcPublicTimelineSegmentRepositoryIntegrationTest {
    @Autowired
    private lateinit var repository: JdbcPublicTimelineSegmentRepository

    @Autowired
    private lateinit var ownershipRepository: JdbcOwnershipRepository

    @Autowired
    private lateinit var mediaAssetRepository: JdbcMediaAssetRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val now: Instant = Instant.parse("2026-06-17T00:00:00Z")

    @BeforeEach
    fun deleteRecords() {
        jdbcTemplate.execute("delete from media_assets")
        jdbcTemplate.execute("delete from ownership_records")
    }

    @Test
    fun `finds approved media segments overlapping requested range`() {
        val ownership = ownershipRepository.save(activeOwnership(startSecond = 10, endSecond = 20))
        val approved = mediaAssetRepository.save(approvedMediaAsset(ownershipRecordId = ownership.id))
        mediaAssetRepository.save(uploadedMediaAsset(ownershipRecordId = ownership.id))

        val result = repository.findApprovedOverlapping(TimeRange(startSecond = 0, endSecond = 30))

        assertThat(result).hasSize(1)
        assertThat(result[0].range).isEqualTo(TimeRange(startSecond = 10, endSecond = 20))
        assertThat(result[0].mediaAssetId).isEqualTo(approved.id)
        assertThat(result[0].mediaUrl).isEqualTo(approved.approvedFileUrl)
    }

    @Test
    fun `clips segments to requested range`() {
        val ownership = ownershipRepository.save(activeOwnership(startSecond = 10, endSecond = 20))
        val approved = mediaAssetRepository.save(approvedMediaAsset(ownershipRecordId = ownership.id))

        val result = repository.findApprovedOverlapping(TimeRange(startSecond = 15, endSecond = 18))

        assertThat(result).hasSize(1)
        assertThat(result[0].range).isEqualTo(TimeRange(startSecond = 15, endSecond = 18))
        assertThat(result[0].mediaAssetId).isEqualTo(approved.id)
    }

    @Test
    fun `excludes hidden and rejected media`() {
        val ownership = ownershipRepository.save(activeOwnership(startSecond = 10, endSecond = 20))
        mediaAssetRepository.save(
            mediaAsset(
                ownershipRecordId = ownership.id,
                moderationStatus = ModerationStatus.HIDDEN,
                approvedFileUrl = "https://cdn.example.com/hidden.png",
            ),
        )
        mediaAssetRepository.save(
            mediaAsset(
                ownershipRecordId = ownership.id,
                moderationStatus = ModerationStatus.REJECTED,
                approvedFileUrl = null,
            ),
        )

        val result = repository.findApprovedOverlapping(TimeRange(startSecond = 0, endSecond = 30))

        assertThat(result).isEmpty()
    }

    @Test
    fun `excludes inactive ownership records`() {
        val inactiveOwnershipId = UUID.randomUUID()
        insertInactiveOwnership(id = inactiveOwnershipId, startSecond = 10, endSecond = 20)
        mediaAssetRepository.save(approvedMediaAsset(ownershipRecordId = inactiveOwnershipId))

        val result = repository.findApprovedOverlapping(TimeRange(startSecond = 0, endSecond = 30))

        assertThat(result).isEmpty()
    }

    private fun activeOwnership(startSecond: Long, endSecond: Long): OwnershipRecord =
        OwnershipRecord.active(
            id = UUID.randomUUID(),
            range = TimeRange(startSecond = startSecond, endSecond = endSecond),
            ownerId = UUID.randomUUID(),
            validFrom = now,
            acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
        )

    private fun approvedMediaAsset(ownershipRecordId: UUID): MediaAsset =
        mediaAsset(
            ownershipRecordId = ownershipRecordId,
            moderationStatus = ModerationStatus.APPROVED,
            approvedFileUrl = "https://cdn.example.com/approved/${UUID.randomUUID()}.png",
        )

    private fun uploadedMediaAsset(ownershipRecordId: UUID): MediaAsset =
        mediaAsset(
            ownershipRecordId = ownershipRecordId,
            moderationStatus = ModerationStatus.UPLOADED,
            approvedFileUrl = null,
        )

    private fun mediaAsset(
        ownershipRecordId: UUID,
        moderationStatus: ModerationStatus,
        approvedFileUrl: String?,
    ): MediaAsset =
        MediaAsset(
            id = UUID.randomUUID(),
            ownershipRecordId = ownershipRecordId,
            ownerId = UUID.randomUUID(),
            mediaType = MediaType.IMAGE,
            originalFileUrl = "s3://bucket/original/${UUID.randomUUID()}.png",
            approvedFileUrl = approvedFileUrl,
            thumbnailUrl = "https://cdn.example.com/thumb/${UUID.randomUUID()}.png",
            externalLink = "https://example.com",
            moderationStatus = moderationStatus,
            createdAt = now,
            updatedAt = now,
        )

    private fun insertInactiveOwnership(id: UUID, startSecond: Long, endSecond: Long) {
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
                created_at,
                updated_at
            ) values (?, ?, ?, ?, 'TRANSFERRED', ?, ?, 'PRIMARY_PURCHASE', ?, ?)
            """.trimIndent(),
            id,
            startSecond,
            endSecond,
            UUID.randomUUID(),
            Timestamp.from(now),
            Timestamp.from(now.plusSeconds(1)),
            Timestamp.from(now),
            Timestamp.from(now.plusSeconds(1)),
        )
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("time_archive")
            .withUsername("time_archive")
            .withPassword("time_archive")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
