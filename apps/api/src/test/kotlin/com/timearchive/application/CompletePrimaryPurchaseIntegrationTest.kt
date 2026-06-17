package com.timearchive.application

import com.timearchive.adapter.outbound.persistence.JdbcAuditLogAdapter
import com.timearchive.adapter.outbound.persistence.JdbcOutboxAdapter
import com.timearchive.adapter.outbound.persistence.JdbcOwnershipRepository
import com.timearchive.adapter.outbound.persistence.JdbcPaymentEventRepository
import com.timearchive.adapter.outbound.persistence.JdbcPurchaseRepository
import com.timearchive.adapter.outbound.persistence.JdbcPurchaseReservationRepository
import com.timearchive.adapter.outbound.persistence.SpringTransactionAdapter
import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.ClockPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
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
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
class CompletePrimaryPurchaseIntegrationTest {
    @Autowired
    private lateinit var transactionAdapter: SpringTransactionAdapter

    @Autowired
    private lateinit var reservationRepository: JdbcPurchaseReservationRepository

    @Autowired
    private lateinit var purchaseRepository: JdbcPurchaseRepository

    @Autowired
    private lateinit var paymentEventRepository: JdbcPaymentEventRepository

    @Autowired
    private lateinit var ownershipRepository: JdbcOwnershipRepository

    @Autowired
    private lateinit var auditLogAdapter: JdbcAuditLogAdapter

    @Autowired
    private lateinit var outboxAdapter: JdbcOutboxAdapter

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val now: Instant = Instant.parse("2026-06-16T00:00:00Z")

    @BeforeEach
    fun deleteRecords() {
        jdbcTemplate.execute("delete from outbox_events")
        jdbcTemplate.execute("delete from audit_logs")
        jdbcTemplate.execute("delete from ownership_records")
        jdbcTemplate.execute("delete from purchases")
        jdbcTemplate.execute("delete from payment_events")
        jdbcTemplate.execute("delete from purchase_reservations")
    }

    @Test
    fun `finalizes paid reservation into purchase ownership audit logs and outbox events`() {
        val reservation = reservationRepository.save(heldReservation())
        val useCase = useCase()

        val result = useCase.complete(command(reservation.id))

        assertThat(result.alreadyProcessed).isFalse()
        assertThat(count("purchases")).isEqualTo(1)
        assertThat(count("ownership_records")).isEqualTo(1)
        assertThat(count("payment_events")).isEqualTo(1)
        assertThat(count("audit_logs")).isEqualTo(2)
        assertThat(count("outbox_events")).isEqualTo(3)
        assertThat(reservationStatus(reservation.id)).isEqualTo("COMPLETED")
        assertThat(paymentEventStatus()).isEqualTo("PROCESSED")
    }

    @Test
    fun `processes duplicate payment event without duplicate ownership`() {
        val reservation = reservationRepository.save(heldReservation())
        val useCase = useCase()

        val first = useCase.complete(command(reservation.id))
        val second = useCase.complete(command(reservation.id))

        assertThat(first.alreadyProcessed).isFalse()
        assertThat(second.alreadyProcessed).isTrue()
        assertThat(second.purchaseId).isEqualTo(first.purchaseId)
        assertThat(count("purchases")).isEqualTo(1)
        assertThat(count("ownership_records")).isEqualTo(1)
        assertThat(count("payment_events")).isEqualTo(1)
        assertThat(count("audit_logs")).isEqualTo(2)
        assertThat(count("outbox_events")).isEqualTo(3)
    }

    @Test
    fun `rolls back when reservation is expired`() {
        val reservation = reservationRepository.save(
            heldReservation(
                createdAt = now.minusSeconds(1_200),
                expiresAt = now.minusSeconds(600),
            ),
        )
        val useCase = useCase()

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.complete(command(reservation.id)) }
            .withMessage("reservation is expired")

        assertThat(count("purchases")).isZero()
        assertThat(count("ownership_records")).isZero()
        assertThat(count("payment_events")).isZero()
        assertThat(reservationStatus(reservation.id)).isEqualTo("HELD")
    }

    @Test
    fun `rolls back when ownership appears before payment finalization`() {
        val reservation = reservationRepository.save(heldReservation())
        ownershipRepository.save(
            OwnershipRecord.active(
                id = UUID.randomUUID(),
                range = reservation.range,
                ownerId = UUID.randomUUID(),
                validFrom = now,
                acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
            ),
        )
        val useCase = useCase()

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.complete(command(reservation.id)) }
            .withMessage("time range already has active ownership")

        assertThat(count("purchases")).isZero()
        assertThat(count("payment_events")).isZero()
        assertThat(count("audit_logs")).isZero()
        assertThat(count("outbox_events")).isZero()
        assertThat(reservationStatus(reservation.id)).isEqualTo("HELD")
    }

    private fun useCase(): CompletePrimaryPurchase =
        CompletePrimaryPurchase(
            transactionPort = transactionAdapter,
            purchaseReservationRepository = reservationRepository,
            purchaseRepository = purchaseRepository,
            paymentEventRepository = paymentEventRepository,
            ownershipRepository = ownershipRepository,
            auditLogPort = auditLogAdapter,
            outboxPort = outboxAdapter,
            clockPort = ClockPort { now },
            idGenerator = UUID::randomUUID,
        )

    private fun command(reservationId: UUID): CompletePrimaryPurchase.Command =
        CompletePrimaryPurchase.Command(
            provider = "stripe",
            providerEventId = "evt_1",
            eventType = "payment_intent.succeeded",
            payloadHash = "hash",
            reservationId = reservationId,
            paymentReference = "pi_test",
            requestId = "request-1",
        )

    private fun heldReservation(
        createdAt: Instant = now,
        expiresAt: Instant = now.plusSeconds(600),
    ): PurchaseReservation =
        PurchaseReservation.held(
            id = UUID.randomUUID(),
            buyerId = UUID.randomUUID(),
            range = TimeRange(startSecond = 10, endSecond = 12),
            now = createdAt,
            expiresAt = expiresAt,
        )

    private fun count(tableName: String): Int =
        jdbcTemplate.queryForObject("select count(*) from $tableName", Int::class.java) ?: 0

    private fun reservationStatus(id: UUID): String =
        jdbcTemplate.queryForObject(
            "select status from purchase_reservations where id = ?",
            String::class.java,
            id,
        ) ?: ""

    private fun paymentEventStatus(): String =
        jdbcTemplate.queryForObject(
            "select processing_status from payment_events where provider = 'stripe' and provider_event_id = 'evt_1'",
            String::class.java,
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
