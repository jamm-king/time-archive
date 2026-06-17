package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.AcquisitionType
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
class JdbcOwnershipRepositoryIntegrationTest {
    @Autowired
    private lateinit var repository: JdbcOwnershipRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun deleteRecords() {
        jdbcTemplate.execute("delete from ownership_records")
    }

    @Test
    fun `saves and finds active overlapping ownership`() {
        val record = activeOwnership(startSecond = 10, endSecond = 20)

        repository.save(record)

        val result = repository.findActiveOverlapping(TimeRange(startSecond = 15, endSecond = 16))

        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo(record.id)
        assertThat(result.first().range).isEqualTo(record.range)
    }

    @Test
    fun `finds ownership by id`() {
        val record = activeOwnership(startSecond = 10, endSecond = 20)

        repository.save(record)

        val result = repository.findById(record.id)

        assertThat(result).isEqualTo(record)
    }

    @Test
    fun `rejects overlapping active ownership`() {
        repository.save(activeOwnership(startSecond = 10, endSecond = 20))

        assertThatThrownBy {
            repository.save(activeOwnership(startSecond = 19, endSecond = 30))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `allows adjacent active ownership`() {
        repository.save(activeOwnership(startSecond = 10, endSecond = 20))
        repository.save(activeOwnership(startSecond = 20, endSecond = 30))

        val result = repository.findActiveOverlapping(TimeRange(startSecond = 19, endSecond = 21))

        assertThat(result).hasSize(2)
    }

    @Test
    fun `allows overlapping historical ownership`() {
        val now = Instant.parse("2026-06-16T00:00:00Z")
        repository.save(
            OwnershipRecord(
                id = UUID.randomUUID(),
                range = TimeRange(startSecond = 10, endSecond = 20),
                ownerId = UUID.randomUUID(),
                status = com.timearchive.domain.model.OwnershipStatus.TRANSFERRED,
                validFrom = now,
                validUntil = now.plusSeconds(10),
                acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
                sourcePurchaseId = null,
                sourceTransactionId = null,
                createdAt = now,
                updatedAt = now.plusSeconds(10),
            ),
        )

        repository.save(activeOwnership(startSecond = 10, endSecond = 20))

        val result = repository.findActiveOverlapping(TimeRange(startSecond = 10, endSecond = 20))

        assertThat(result).hasSize(1)
    }

    @Test
    fun `rejects ownership beyond canonical timeline`() {
        assertThatThrownBy {
            insertInvalidRange(startSecond = 86_399, endSecond = 86_401)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun activeOwnership(
        startSecond: Long,
        endSecond: Long,
    ): OwnershipRecord =
        OwnershipRecord.active(
            id = UUID.randomUUID(),
            range = TimeRange(startSecond = startSecond, endSecond = endSecond),
            ownerId = UUID.randomUUID(),
            validFrom = Instant.parse("2026-06-16T00:00:00Z"),
            acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
        )

    private fun insertInvalidRange(
        startSecond: Long,
        endSecond: Long,
    ) {
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
            ) values (?, ?, ?, ?, 'ACTIVE', now(), null, 'PRIMARY_PURCHASE', now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            startSecond,
            endSecond,
            UUID.randomUUID(),
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
