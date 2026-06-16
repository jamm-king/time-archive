package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.PurchaseReservationStatus
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
class JdbcPurchaseReservationRepositoryIntegrationTest {
    @Autowired
    private lateinit var repository: JdbcPurchaseReservationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun deleteRecords() {
        jdbcTemplate.execute("delete from purchase_reservations")
    }

    @Test
    fun `saves and finds active overlapping reservation`() {
        val reservation = heldReservation(startSecond = 10, endSecond = 20)

        repository.save(reservation)

        val result = repository.findActiveOverlapping(TimeRange(startSecond = 15, endSecond = 16))

        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo(reservation.id)
        assertThat(result.first().range).isEqualTo(reservation.range)
    }

    @Test
    fun `rejects overlapping active reservation`() {
        repository.save(heldReservation(startSecond = 10, endSecond = 20))

        assertThatThrownBy {
            repository.save(heldReservation(startSecond = 19, endSecond = 30))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `allows adjacent active reservation`() {
        repository.save(heldReservation(startSecond = 10, endSecond = 20))
        repository.save(heldReservation(startSecond = 20, endSecond = 30))

        val result = repository.findActiveOverlapping(TimeRange(startSecond = 19, endSecond = 21))

        assertThat(result).hasSize(2)
    }

    @Test
    fun `allows overlapping expired reservation`() {
        repository.save(expiredReservation(startSecond = 10, endSecond = 20))
        repository.save(heldReservation(startSecond = 10, endSecond = 20))

        val result = repository.findActiveOverlapping(TimeRange(startSecond = 10, endSecond = 20))

        assertThat(result).hasSize(1)
    }

    @Test
    fun `rejects reservation beyond canonical timeline`() {
        assertThatThrownBy {
            insertInvalidRange(startSecond = 86_399, endSecond = 86_401)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `expires overdue active reservations`() {
        val now = Instant.parse("2026-06-16T00:00:00Z")
        repository.save(
            heldReservation(
                startSecond = 10,
                endSecond = 20,
                now = now.minusSeconds(900),
                expiresAt = now.minusSeconds(300),
            ),
        )

        val updated = repository.expireOverdue(now)

        assertThat(updated).isEqualTo(1)
        assertThat(repository.findActiveOverlapping(TimeRange(startSecond = 10, endSecond = 20))).isEmpty()
    }

    @Test
    fun `marks held reservation as checkout created`() {
        val now = Instant.parse("2026-06-16T00:00:00Z")
        val reservation = repository.save(heldReservation(startSecond = 10, endSecond = 20))

        val updated = repository.markCheckoutCreated(reservation.id, now)

        assertThat(updated).isEqualTo(1)
        assertThat(reservationStatus(reservation.id)).isEqualTo("CHECKOUT_CREATED")
    }

    @Test
    fun `does not mark non held reservation as checkout created`() {
        val now = Instant.parse("2026-06-16T00:00:00Z")
        val reservation = repository.save(heldReservation(startSecond = 10, endSecond = 20))
        repository.markCheckoutCreated(reservation.id, now)

        val updated = repository.markCheckoutCreated(reservation.id, now)

        assertThat(updated).isZero()
        assertThat(reservationStatus(reservation.id)).isEqualTo("CHECKOUT_CREATED")
    }

    private fun heldReservation(
        startSecond: Long,
        endSecond: Long,
        now: Instant = Instant.parse("2026-06-16T00:00:00Z"),
        expiresAt: Instant = now.plusSeconds(600),
    ): PurchaseReservation =
        PurchaseReservation.held(
            id = UUID.randomUUID(),
            buyerId = UUID.randomUUID(),
            range = TimeRange(startSecond = startSecond, endSecond = endSecond),
            now = now,
            expiresAt = expiresAt,
        )

    private fun expiredReservation(
        startSecond: Long,
        endSecond: Long,
    ): PurchaseReservation {
        val now = Instant.parse("2026-06-16T00:00:00Z")
        return PurchaseReservation(
            id = UUID.randomUUID(),
            buyerId = UUID.randomUUID(),
            range = TimeRange(startSecond = startSecond, endSecond = endSecond),
            amountCents = (endSecond - startSecond) * 100,
            currency = "USD",
            status = PurchaseReservationStatus.EXPIRED,
            expiresAt = now.minusSeconds(300),
            createdAt = now.minusSeconds(900),
            updatedAt = now,
        )
    }

    private fun insertInvalidRange(
        startSecond: Long,
        endSecond: Long,
    ) {
        jdbcTemplate.update(
            """
            insert into purchase_reservations (
                id,
                buyer_id,
                start_second,
                end_second,
                amount_cents,
                currency,
                status,
                expires_at,
                created_at,
                updated_at
            ) values (?, ?, ?, ?, 200, 'USD', 'HELD', now() + interval '10 minutes', now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            startSecond,
            endSecond,
        )
    }

    private fun reservationStatus(id: UUID): String =
        jdbcTemplate.queryForObject(
            "select status from purchase_reservations where id = ?",
            String::class.java,
            id,
        ) ?: ""

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
