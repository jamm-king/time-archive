package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.ModerationStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
class JdbcMediaAssetRepositoryIntegrationTest {
    @Autowired
    private lateinit var repository: JdbcMediaAssetRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val now: Instant = Instant.parse("2026-06-17T00:00:00Z")

    @BeforeEach
    fun deleteRecords() {
        jdbcTemplate.execute("delete from media_assets")
    }

    @Test
    fun `saves and finds media asset by id`() {
        val asset = repository.save(uploadedMediaAsset())

        val found = repository.findById(asset.id)

        assertThat(found).isEqualTo(asset)
    }

    @Test
    fun `finds media assets by ownership record id`() {
        val ownershipRecordId = UUID.randomUUID()
        val first = repository.save(uploadedMediaAsset(ownershipRecordId = ownershipRecordId))
        val second = repository.save(
            uploadedMediaAsset(
                ownershipRecordId = ownershipRecordId,
                createdAt = now.plusSeconds(1),
            ),
        )
        repository.save(uploadedMediaAsset(ownershipRecordId = UUID.randomUUID()))

        val result = repository.findByOwnershipRecordId(ownershipRecordId)

        assertThat(result.map { it.id }).containsExactly(first.id, second.id)
    }

    @Test
    fun `finds approved media assets by ownership record id`() {
        val ownershipRecordId = UUID.randomUUID()
        val approved = repository.save(approvedMediaAsset(ownershipRecordId = ownershipRecordId))
        repository.save(uploadedMediaAsset(ownershipRecordId = ownershipRecordId))
        repository.save(
            mediaAsset(
                ownershipRecordId = ownershipRecordId,
                approvedFileUrl = "s3://bucket/approved/hidden.png",
                moderationStatus = ModerationStatus.HIDDEN,
            ),
        )

        val result = repository.findApprovedByOwnershipRecordId(ownershipRecordId)

        assertThat(result).containsExactly(approved)
    }

    @Test
    fun `finds media assets by moderation status`() {
        val uploaded = repository.save(uploadedMediaAsset())
        repository.save(approvedMediaAsset())

        val result = repository.findByModerationStatus(ModerationStatus.UPLOADED)

        assertThat(result).containsExactly(uploaded)
    }

    @Test
    fun `updates moderation state`() {
        val uploaded = repository.save(uploadedMediaAsset())
        val approved = uploaded.approve(
            approvedFileUrl = "s3://bucket/approved/${UUID.randomUUID()}.png",
            thumbnailUrl = "s3://bucket/thumb/${UUID.randomUUID()}.png",
            now = uploaded.updatedAt.plusSeconds(1),
        )

        repository.update(approved)

        val result = repository.findById(uploaded.id)
        assertThat(result).isEqualTo(approved)
    }

    @Test
    fun `finds media assets by owner id`() {
        val ownerId = UUID.randomUUID()
        val first = repository.save(uploadedMediaAsset(ownerId = ownerId))
        val second = repository.save(
            uploadedMediaAsset(
                ownerId = ownerId,
                createdAt = now.plusSeconds(1),
            ),
        )
        repository.save(uploadedMediaAsset(ownerId = UUID.randomUUID()))

        val result = repository.findByOwnerId(ownerId)

        assertThat(result.map { it.id }).containsExactly(first.id, second.id)
    }

    @Test
    fun `rejects blank original file url`() {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                insert into media_assets (
                    id,
                    ownership_record_id,
                    owner_id,
                    media_type,
                    original_file_url,
                    moderation_status,
                    created_at,
                    updated_at
                ) values (?, ?, ?, 'IMAGE', ' ', 'UPLOADED', now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `rejects approved media without approved file url`() {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                insert into media_assets (
                    id,
                    ownership_record_id,
                    owner_id,
                    media_type,
                    original_file_url,
                    moderation_status,
                    created_at,
                    updated_at
                ) values (?, ?, ?, 'IMAGE', 's3://bucket/original/image.png', 'APPROVED', now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `rejects invalid moderation status`() {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                insert into media_assets (
                    id,
                    ownership_record_id,
                    owner_id,
                    media_type,
                    original_file_url,
                    moderation_status,
                    created_at,
                    updated_at
                ) values (?, ?, ?, 'IMAGE', 's3://bucket/original/image.png', 'INVALID', now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun uploadedMediaAsset(
        ownershipRecordId: UUID = UUID.randomUUID(),
        ownerId: UUID = UUID.randomUUID(),
        createdAt: Instant = now,
    ): MediaAsset =
        MediaAsset.uploaded(
            id = UUID.randomUUID(),
            ownershipRecordId = ownershipRecordId,
            ownerId = ownerId,
            mediaType = MediaType.IMAGE,
            originalFileUrl = "s3://bucket/original/${UUID.randomUUID()}.png",
            thumbnailUrl = "s3://bucket/thumb/${UUID.randomUUID()}.png",
            externalLink = "https://example.com",
            now = createdAt,
        )

    private fun approvedMediaAsset(
        ownershipRecordId: UUID = UUID.randomUUID(),
    ): MediaAsset =
        mediaAsset(
            ownershipRecordId = ownershipRecordId,
            approvedFileUrl = "s3://bucket/approved/${UUID.randomUUID()}.png",
            moderationStatus = ModerationStatus.APPROVED,
        )

    private fun mediaAsset(
        ownershipRecordId: UUID = UUID.randomUUID(),
        approvedFileUrl: String?,
        moderationStatus: ModerationStatus,
    ): MediaAsset =
        MediaAsset(
            id = UUID.randomUUID(),
            ownershipRecordId = ownershipRecordId,
            ownerId = UUID.randomUUID(),
            mediaType = MediaType.IMAGE,
            originalFileUrl = "s3://bucket/original/${UUID.randomUUID()}.png",
            approvedFileUrl = approvedFileUrl,
            thumbnailUrl = null,
            externalLink = null,
            durationMs = null,
            moderationStatus = moderationStatus,
            createdAt = now,
            updatedAt = now,
        )

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
