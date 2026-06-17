package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.MediaUploadRequest
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.TimeRange
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
class JdbcMediaUploadRequestRepositoryIntegrationTest {
    @Autowired
    private lateinit var uploadRequestRepository: JdbcMediaUploadRequestRepository

    @Autowired
    private lateinit var mediaAssetRepository: JdbcMediaAssetRepository

    @Autowired
    private lateinit var ownershipRepository: JdbcOwnershipRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val now: Instant = Instant.parse("2026-06-17T00:00:00Z")

    @BeforeEach
    fun deleteRecords() {
        jdbcTemplate.execute("delete from media_upload_requests")
        jdbcTemplate.execute("delete from media_assets")
        jdbcTemplate.execute("delete from ownership_records")
    }

    @Test
    fun `saves and finds upload request`() {
        val ownership = ownershipRepository.save(activeOwnership())
        val request = uploadRequest(ownership)

        uploadRequestRepository.save(request)

        assertThat(uploadRequestRepository.findById(request.id)).isEqualTo(request)
        assertThat(uploadRequestRepository.findByOwnershipRecordId(ownership.id)).containsExactly(request)
    }

    @Test
    fun `rejects duplicate object key`() {
        val ownership = ownershipRepository.save(activeOwnership())
        val request = uploadRequest(ownership)
        uploadRequestRepository.save(request)

        assertThatThrownBy {
            uploadRequestRepository.save(request.copy(id = UUID.randomUUID()))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `marks upload request completed with media asset id`() {
        val ownership = ownershipRepository.save(activeOwnership())
        val request = uploadRequest(ownership)
        uploadRequestRepository.save(request)
        val mediaAsset = mediaAssetRepository.save(
            com.timearchive.domain.model.MediaAsset.uploaded(
                id = UUID.randomUUID(),
                ownershipRecordId = ownership.id,
                ownerId = ownership.ownerId,
                mediaType = MediaType.IMAGE,
                originalFileUrl = request.originalFileUrl,
                now = now,
            ),
        )

        val updatedRows = uploadRequestRepository.markCompleted(
            id = request.id,
            mediaAssetId = mediaAsset.id,
            now = now.plusSeconds(1),
        )

        val result = uploadRequestRepository.findById(request.id)
        assertThat(updatedRows).isEqualTo(1)
        assertThat(result?.status?.name).isEqualTo("COMPLETED")
        assertThat(result?.mediaAssetId).isEqualTo(mediaAsset.id)
    }

    private fun activeOwnership(): OwnershipRecord =
        OwnershipRecord.active(
            id = UUID.randomUUID(),
            range = TimeRange(startSecond = 10, endSecond = 11),
            ownerId = UUID.randomUUID(),
            validFrom = now,
            acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
        )

    private fun uploadRequest(ownership: OwnershipRecord): MediaUploadRequest =
        MediaUploadRequest.requested(
            id = UUID.randomUUID(),
            ownershipRecordId = ownership.id,
            ownerId = ownership.ownerId,
            mediaType = MediaType.IMAGE,
            originalFilename = "original.png",
            contentType = "image/png",
            contentLengthBytes = 1024,
            objectKey = "media/originals/${ownership.ownerId}/${ownership.id}/object/original.png",
            originalFileUrl = "http://localhost:9000/time-archive-media/object",
            now = now,
            expiresAt = now.plusSeconds(600),
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
