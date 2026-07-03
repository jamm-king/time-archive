package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.model.CheckoutAttempt
import com.timearchive.domain.model.CheckoutAttemptStatus
import com.timearchive.domain.model.PurchaseReservation
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
class JdbcCheckoutAttemptRepositoryIntegrationTest {
    @Autowired
    private lateinit var checkoutAttemptRepository: JdbcCheckoutAttemptRepository

    @Autowired
    private lateinit var purchaseReservationRepository: JdbcPurchaseReservationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun deleteRecords() {
        jdbcTemplate.execute("delete from checkout_attempts")
        jdbcTemplate.execute("delete from purchase_reservations")
    }

    @Test
    fun `saves and finds checkout attempt by reservation id`() {
        val reservation = purchaseReservationRepository.save(heldReservation())
        val attempt = checkoutAttemptRepository.save(pendingAttempt(reservation))

        val found = checkoutAttemptRepository.findByReservationIdForUpdate(reservation.id)

        assertThat(found).isEqualTo(attempt)
    }

    @Test
    fun `rejects multiple checkout attempts for the same reservation`() {
        val reservation = purchaseReservationRepository.save(heldReservation())
        checkoutAttemptRepository.save(pendingAttempt(reservation))

        assertThatThrownBy {
            checkoutAttemptRepository.save(pendingAttempt(reservation))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `marks provider created with reference and checkout url`() {
        val now = Instant.parse("2026-07-03T00:00:00Z")
        val reservation = purchaseReservationRepository.save(heldReservation())
        val attempt = checkoutAttemptRepository.save(pendingAttempt(reservation))

        val updated = checkoutAttemptRepository.markProviderCreated(
            id = attempt.id,
            providerReference = "paypal-order-1",
            checkoutUrl = "https://www.sandbox.paypal.com/checkoutnow?token=paypal-order-1",
            now = now,
        )

        val found = checkoutAttemptRepository.findByReservationIdForUpdate(reservation.id)
        assertThat(updated).isEqualTo(1)
        assertThat(found?.status).isEqualTo(CheckoutAttemptStatus.PROVIDER_CREATED)
        assertThat(found?.providerReference).isEqualTo("paypal-order-1")
        assertThat(found?.checkoutUrl).isEqualTo("https://www.sandbox.paypal.com/checkoutnow?token=paypal-order-1")
    }

    @Test
    fun `marks provider failed and keeps provider request id reusable`() {
        val now = Instant.parse("2026-07-03T00:00:00Z")
        val reservation = purchaseReservationRepository.save(heldReservation())
        val attempt = checkoutAttemptRepository.save(pendingAttempt(reservation))

        val updated = checkoutAttemptRepository.markProviderFailed(
            id = attempt.id,
            now = now,
        )

        val found = checkoutAttemptRepository.findByReservationIdForUpdate(reservation.id)
        assertThat(updated).isEqualTo(1)
        assertThat(found?.status).isEqualTo(CheckoutAttemptStatus.PROVIDER_FAILED)
        assertThat(found?.providerRequestId).isEqualTo(attempt.providerRequestId)
    }

    private fun heldReservation(): PurchaseReservation =
        PurchaseReservation.held(
            id = UUID.randomUUID(),
            buyerId = UUID.randomUUID(),
            range = TimeRange(startSecond = 10, endSecond = 12),
            now = Instant.parse("2026-07-03T00:00:00Z"),
            expiresAt = Instant.parse("2026-07-03T00:10:00Z"),
        )

    private fun pendingAttempt(reservation: PurchaseReservation): CheckoutAttempt =
        CheckoutAttempt.pending(
            id = UUID.randomUUID(),
            reservation = reservation,
            provider = "paypal",
            providerRequestId = UUID.randomUUID().toString(),
            now = Instant.parse("2026-07-03T00:00:00Z"),
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
