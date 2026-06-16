package com.timearchive.application

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.PurchaseReservationRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CheckAvailabilityTest {
    private val now: Instant = Instant.parse("2026-06-16T00:00:00Z")

    @Test
    fun `returns available when there are no conflicts`() {
        val ownershipRepository = FakeOwnershipRepository()
        val reservationRepository = FakePurchaseReservationRepository()
        val useCase = useCase(
            ownershipRepository = ownershipRepository,
            purchaseReservationRepository = reservationRepository,
        )

        val result = useCase.check(CheckAvailability.Query(startSecond = 10, endSecond = 20))

        assertThat(result.available).isTrue()
        assertThat(result.conflicts).isEmpty()
        assertThat(result.range).isEqualTo(TimeRange(startSecond = 10, endSecond = 20))
        assertThat(reservationRepository.expireOverdueCalls).containsExactly(now)
    }

    @Test
    fun `returns unavailable when ownership overlaps`() {
        val ownership = activeOwnership(startSecond = 15, endSecond = 25)
        val useCase = useCase(
            ownershipRepository = FakeOwnershipRepository(activeOverlaps = listOf(ownership)),
            purchaseReservationRepository = FakePurchaseReservationRepository(),
        )

        val result = useCase.check(CheckAvailability.Query(startSecond = 10, endSecond = 20))

        assertThat(result.available).isFalse()
        assertThat(result.conflicts).containsExactly(
            CheckAvailability.Conflict(
                type = CheckAvailability.ConflictType.OWNERSHIP,
                range = ownership.range,
            ),
        )
    }

    @Test
    fun `returns unavailable when active reservation overlaps`() {
        val reservation = heldReservation(startSecond = 12, endSecond = 18)
        val useCase = useCase(
            ownershipRepository = FakeOwnershipRepository(),
            purchaseReservationRepository = FakePurchaseReservationRepository(activeOverlaps = listOf(reservation)),
        )

        val result = useCase.check(CheckAvailability.Query(startSecond = 10, endSecond = 20))

        assertThat(result.available).isFalse()
        assertThat(result.conflicts).containsExactly(
            CheckAvailability.Conflict(
                type = CheckAvailability.ConflictType.RESERVATION,
                range = reservation.range,
            ),
        )
    }

    @Test
    fun `returns all conflict summaries`() {
        val ownership = activeOwnership(startSecond = 10, endSecond = 12)
        val reservation = heldReservation(startSecond = 18, endSecond = 20)
        val useCase = useCase(
            ownershipRepository = FakeOwnershipRepository(activeOverlaps = listOf(ownership)),
            purchaseReservationRepository = FakePurchaseReservationRepository(activeOverlaps = listOf(reservation)),
        )

        val result = useCase.check(CheckAvailability.Query(startSecond = 10, endSecond = 20))

        assertThat(result.available).isFalse()
        assertThat(result.conflicts.map { it.type }).containsExactly(
            CheckAvailability.ConflictType.OWNERSHIP,
            CheckAvailability.ConflictType.RESERVATION,
        )
    }

    @Test
    fun `rejects invalid range ordering`() {
        val useCase = useCase(
            ownershipRepository = FakeOwnershipRepository(),
            purchaseReservationRepository = FakePurchaseReservationRepository(),
        )

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.check(CheckAvailability.Query(startSecond = 20, endSecond = 20)) }
            .withMessage("endSecond must be greater than startSecond")
    }

    @Test
    fun `rejects range outside canonical timeline`() {
        val useCase = useCase(
            ownershipRepository = FakeOwnershipRepository(),
            purchaseReservationRepository = FakePurchaseReservationRepository(),
        )

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.check(CheckAvailability.Query(startSecond = 86_399, endSecond = 86_401)) }
            .withMessage("endSecond must be less than or equal to totalSeconds")
    }

    private fun useCase(
        ownershipRepository: OwnershipRepository,
        purchaseReservationRepository: PurchaseReservationRepository,
    ): CheckAvailability =
        CheckAvailability(
            ownershipRepository = ownershipRepository,
            purchaseReservationRepository = purchaseReservationRepository,
            clockPort = ClockPort { now },
        )

    private fun activeOwnership(
        startSecond: Long,
        endSecond: Long,
    ): OwnershipRecord =
        OwnershipRecord.active(
            id = UUID.randomUUID(),
            range = TimeRange(startSecond = startSecond, endSecond = endSecond),
            ownerId = UUID.randomUUID(),
            validFrom = now,
            acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
        )

    private fun heldReservation(
        startSecond: Long,
        endSecond: Long,
    ): PurchaseReservation =
        PurchaseReservation.held(
            id = UUID.randomUUID(),
            buyerId = UUID.randomUUID(),
            range = TimeRange(startSecond = startSecond, endSecond = endSecond),
            now = now,
            expiresAt = now.plusSeconds(600),
        )

    private class FakeOwnershipRepository(
        private val activeOverlaps: List<OwnershipRecord> = emptyList(),
    ) : OwnershipRepository {
        override fun save(record: OwnershipRecord): OwnershipRecord = record

        override fun findActiveOverlapping(range: TimeRange): List<OwnershipRecord> = activeOverlaps
    }

    private class FakePurchaseReservationRepository(
        private val activeOverlaps: List<PurchaseReservation> = emptyList(),
    ) : PurchaseReservationRepository {
        val expireOverdueCalls = mutableListOf<Instant>()

        override fun save(reservation: PurchaseReservation): PurchaseReservation = reservation

        override fun findByIdForUpdate(id: UUID): PurchaseReservation? = null

        override fun findActiveOverlapping(range: TimeRange): List<PurchaseReservation> = activeOverlaps

        override fun expireOverdue(now: Instant): Int {
            expireOverdueCalls.add(now)
            return 0
        }

        override fun markCheckoutCreated(
            id: UUID,
            now: Instant,
        ): Int = 0

        override fun markCompleted(
            id: UUID,
            now: Instant,
        ): Int = 0
    }
}
