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
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ReserveTimeRangeTest {
    private val now = Instant.parse("2026-06-16T00:00:00Z")

    @Test
    fun `creates held reservation for available range`() {
        val reservationRepository = FakePurchaseReservationRepository()
        val useCase = useCase(
            ownershipRepository = FakeOwnershipRepository(),
            reservationRepository = reservationRepository,
        )
        val buyerId = UUID.randomUUID()
        val reservationId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        val reservation = useCase.reserve(
            ReserveTimeRange.Command(
                buyerId = buyerId,
                startSecond = 10,
                endSecond = 12,
            ),
        )

        assertThat(reservation.id).isEqualTo(reservationId)
        assertThat(reservation.buyerId).isEqualTo(buyerId)
        assertThat(reservation.amountCents).isEqualTo(200)
        assertThat(reservation.expiresAt).isEqualTo(now.plusSeconds(600))
        assertThat(reservationRepository.saved).containsExactly(reservation)
        assertThat(reservationRepository.expireOverdueCalledWith).isEqualTo(now)
    }

    @Test
    fun `rejects range overlapping active ownership`() {
        val useCase = useCase(
            ownershipRepository = FakeOwnershipRepository(
                overlaps = listOf(activeOwnership(startSecond = 10, endSecond = 20)),
            ),
            reservationRepository = FakePurchaseReservationRepository(),
        )

        assertThatIllegalArgumentException()
            .isThrownBy {
                useCase.reserve(
                    ReserveTimeRange.Command(
                        buyerId = UUID.randomUUID(),
                        startSecond = 15,
                        endSecond = 16,
                    ),
                )
            }
            .withMessage("time range already has active ownership")
    }

    @Test
    fun `rejects range overlapping active reservation`() {
        val useCase = useCase(
            ownershipRepository = FakeOwnershipRepository(),
            reservationRepository = FakePurchaseReservationRepository(
                overlaps = listOf(heldReservation(startSecond = 10, endSecond = 20)),
            ),
        )

        assertThatIllegalArgumentException()
            .isThrownBy {
                useCase.reserve(
                    ReserveTimeRange.Command(
                        buyerId = UUID.randomUUID(),
                        startSecond = 15,
                        endSecond = 16,
                    ),
                )
            }
            .withMessage("time range already has active reservation")
    }

    private fun useCase(
        ownershipRepository: OwnershipRepository,
        reservationRepository: PurchaseReservationRepository,
    ): ReserveTimeRange =
        ReserveTimeRange(
            ownershipRepository = ownershipRepository,
            purchaseReservationRepository = reservationRepository,
            clockPort = ClockPort { now },
            reservationDuration = Duration.ofMinutes(10),
            idGenerator = { UUID.fromString("00000000-0000-0000-0000-000000000001") },
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
        private val overlaps: List<OwnershipRecord> = emptyList(),
    ) : OwnershipRepository {
        override fun save(record: OwnershipRecord): OwnershipRecord = record

        override fun findById(id: UUID): OwnershipRecord? = null

        override fun findActiveByOwnerId(ownerId: UUID): List<OwnershipRecord> = emptyList()

        override fun findActiveOverlapping(range: TimeRange): List<OwnershipRecord> = overlaps
    }

    private class FakePurchaseReservationRepository(
        private val overlaps: List<PurchaseReservation> = emptyList(),
    ) : PurchaseReservationRepository {
        val saved = mutableListOf<PurchaseReservation>()
        var expireOverdueCalledWith: Instant? = null

        override fun save(reservation: PurchaseReservation): PurchaseReservation {
            saved.add(reservation)
            return reservation
        }

        override fun findByIdForUpdate(id: UUID): PurchaseReservation? = saved.find { it.id == id }

        override fun findActiveOverlapping(range: TimeRange): List<PurchaseReservation> = overlaps

        override fun expireOverdue(now: Instant): Int {
            expireOverdueCalledWith = now
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
