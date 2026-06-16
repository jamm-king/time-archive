package com.timearchive.application

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.AuditLog
import com.timearchive.domain.model.OutboxEvent
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.PaymentEvent
import com.timearchive.domain.model.Purchase
import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.AuditLogPort
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.OutboxPort
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.PaymentEventRepository
import com.timearchive.domain.port.PurchaseRepository
import com.timearchive.domain.port.PurchaseReservationRepository
import com.timearchive.domain.port.TransactionPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CompletePrimaryPurchaseTest {
    private val now: Instant = Instant.parse("2026-06-16T00:00:00Z")

    @Test
    fun `finalizes reservation into purchase ownership audit logs and outbox events`() {
        val reservation = heldReservation()
        val reservationRepository = FakePurchaseReservationRepository(reservation = reservation)
        val purchaseRepository = FakePurchaseRepository()
        val ownershipRepository = FakeOwnershipRepository()
        val auditLogPort = FakeAuditLogPort()
        val outboxPort = FakeOutboxPort()
        val useCase = useCase(
            reservationRepository = reservationRepository,
            purchaseRepository = purchaseRepository,
            ownershipRepository = ownershipRepository,
            auditLogPort = auditLogPort,
            outboxPort = outboxPort,
        )

        val result = useCase.complete(command(reservationId = reservation.id))

        assertThat(result.alreadyProcessed).isFalse()
        assertThat(purchaseRepository.saved).hasSize(1)
        assertThat(ownershipRepository.saved).hasSize(1)
        assertThat(reservationRepository.completedIds).containsExactly(reservation.id)
        assertThat(auditLogPort.logs).hasSize(2)
        assertThat(outboxPort.events).extracting<String> { it.eventType }
            .containsExactly("PurchaseCompleted", "OwnershipCreated", "TimelineManifestInvalidated")
    }

    @Test
    fun `returns idempotent result for already processed payment event`() {
        val reservation = heldReservation()
        val purchase = Purchase.ownershipGranted(
            id = UUID.randomUUID(),
            reservation = reservation,
            paymentProvider = "stripe",
            paymentReference = "pi_test",
            now = now,
        )
        val useCase = useCase(
            paymentEventRepository = FakePaymentEventRepository(
                event = PaymentEvent(
                    id = UUID.randomUUID(),
                    provider = "stripe",
                    providerEventId = "evt_1",
                    eventType = "payment_intent.succeeded",
                    payloadHash = "hash",
                    processingStatus = com.timearchive.domain.model.PaymentEventStatus.PROCESSED,
                    receivedAt = now,
                    processedAt = now,
                ),
            ),
            purchaseRepository = FakePurchaseRepository(existing = purchase),
            reservationRepository = FakePurchaseReservationRepository(reservation = reservation),
        )

        val result = useCase.complete(command(reservationId = reservation.id))

        assertThat(result.alreadyProcessed).isTrue()
        assertThat(result.purchaseId).isEqualTo(purchase.id)
        assertThat(result.ownershipRecordId).isNull()
    }

    @Test
    fun `rejects expired reservation`() {
        val reservation = heldReservation(
            createdAt = now.minusSeconds(1_200),
            expiresAt = now.minusSeconds(600),
        )
        val useCase = useCase(
            reservationRepository = FakePurchaseReservationRepository(reservation = reservation),
        )

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.complete(command(reservationId = reservation.id)) }
            .withMessage("reservation is expired")
    }

    @Test
    fun `rejects ownership overlap before finalization`() {
        val reservation = heldReservation()
        val useCase = useCase(
            reservationRepository = FakePurchaseReservationRepository(reservation = reservation),
            ownershipRepository = FakeOwnershipRepository(
                overlaps = listOf(
                    OwnershipRecord.active(
                        id = UUID.randomUUID(),
                        range = reservation.range,
                        ownerId = UUID.randomUUID(),
                        validFrom = now,
                        acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
                    ),
                ),
            ),
        )

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.complete(command(reservationId = reservation.id)) }
            .withMessage("time range already has active ownership")
    }

    private fun useCase(
        reservationRepository: PurchaseReservationRepository = FakePurchaseReservationRepository(heldReservation()),
        purchaseRepository: FakePurchaseRepository = FakePurchaseRepository(),
        paymentEventRepository: PaymentEventRepository = FakePaymentEventRepository(),
        ownershipRepository: OwnershipRepository = FakeOwnershipRepository(),
        auditLogPort: AuditLogPort = FakeAuditLogPort(),
        outboxPort: OutboxPort = FakeOutboxPort(),
    ): CompletePrimaryPurchase =
        CompletePrimaryPurchase(
            transactionPort = ImmediateTransactionPort,
            purchaseReservationRepository = reservationRepository,
            purchaseRepository = purchaseRepository,
            paymentEventRepository = paymentEventRepository,
            ownershipRepository = ownershipRepository,
            auditLogPort = auditLogPort,
            outboxPort = outboxPort,
            clockPort = ClockPort { now },
            idGenerator = deterministicIds(),
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

    private fun deterministicIds(): () -> UUID {
        var next = 1L
        return {
            UUID.fromString("00000000-0000-0000-0000-${next.toString().padStart(12, '0')}")
                .also { next += 1 }
        }
    }

    private object ImmediateTransactionPort : TransactionPort {
        override fun <T> execute(block: () -> T): T = block()
    }

    private class FakePurchaseReservationRepository(
        private val reservation: PurchaseReservation?,
    ) : PurchaseReservationRepository {
        val completedIds = mutableListOf<UUID>()

        override fun save(reservation: PurchaseReservation): PurchaseReservation = reservation

        override fun findByIdForUpdate(id: UUID): PurchaseReservation? = reservation?.takeIf { it.id == id }

        override fun findActiveOverlapping(range: TimeRange): List<PurchaseReservation> = emptyList()

        override fun expireOverdue(now: Instant): Int = 0

        override fun markCheckoutCreated(
            id: UUID,
            now: Instant,
        ): Int = 0

        override fun markCompleted(
            id: UUID,
            now: Instant,
        ): Int {
            completedIds.add(id)
            return 1
        }
    }

    private class FakePurchaseRepository(
        private val existing: Purchase? = null,
    ) : PurchaseRepository {
        val saved = mutableListOf<Purchase>()

        override fun save(purchase: Purchase): Purchase {
            saved.add(purchase)
            return purchase
        }

        override fun findByReservationId(reservationId: UUID): Purchase? =
            existing ?: saved.find { it.reservationId == reservationId }
    }

    private class FakePaymentEventRepository(
        private val event: PaymentEvent? = null,
    ) : PaymentEventRepository {
        val saved = mutableListOf<PaymentEvent>()
        val processed = mutableListOf<String>()

        override fun save(event: PaymentEvent): PaymentEvent {
            saved.add(event)
            return event
        }

        override fun findByProviderAndEventId(
            provider: String,
            providerEventId: String,
        ): PaymentEvent? = event ?: saved.find { it.provider == provider && it.providerEventId == providerEventId }

        override fun markProcessed(
            provider: String,
            providerEventId: String,
            processedAt: Instant,
        ): Int {
            processed.add("$provider:$providerEventId")
            return 1
        }
    }

    private class FakeOwnershipRepository(
        private val overlaps: List<OwnershipRecord> = emptyList(),
    ) : OwnershipRepository {
        val saved = mutableListOf<OwnershipRecord>()

        override fun save(record: OwnershipRecord): OwnershipRecord {
            saved.add(record)
            return record
        }

        override fun findActiveOverlapping(range: TimeRange): List<OwnershipRecord> = overlaps
    }

    private class FakeAuditLogPort : AuditLogPort {
        val logs = mutableListOf<AuditLog>()

        override fun append(log: AuditLog): AuditLog {
            logs.add(log)
            return log
        }
    }

    private class FakeOutboxPort : OutboxPort {
        val events = mutableListOf<OutboxEvent>()

        override fun append(event: OutboxEvent): OutboxEvent {
            events.add(event)
            return event
        }
    }
}
