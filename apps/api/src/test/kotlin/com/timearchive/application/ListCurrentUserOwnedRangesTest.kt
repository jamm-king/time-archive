package com.timearchive.application

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.OwnershipRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ListCurrentUserOwnedRangesTest {
    private val ownerId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000901")

    @Test
    fun `lists active ownership records for current user`() {
        val expected = listOf(activeOwnership(startSecond = 10, endSecond = 12, ownerId = ownerId))
        val useCase = ListCurrentUserOwnedRanges(
            ownershipRepository = FakeOwnershipRepository(records = expected),
        )

        val result = useCase.list(ListCurrentUserOwnedRanges.Query(currentUserId = ownerId))

        assertThat(result).containsExactlyElementsOf(expected)
    }

    private fun activeOwnership(
        startSecond: Long,
        endSecond: Long,
        ownerId: UUID,
    ): OwnershipRecord =
        OwnershipRecord.active(
            id = UUID.randomUUID(),
            range = TimeRange(startSecond = startSecond, endSecond = endSecond),
            ownerId = ownerId,
            validFrom = Instant.parse("2026-06-18T00:00:00Z"),
            acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
        )

    private class FakeOwnershipRepository(
        private val records: List<OwnershipRecord>,
    ) : OwnershipRepository {
        override fun save(record: OwnershipRecord): OwnershipRecord = record

        override fun findById(id: UUID): OwnershipRecord? = records.find { it.id == id }

        override fun findActiveByOwnerId(ownerId: UUID): List<OwnershipRecord> =
            records.filter { it.ownerId == ownerId && it.isActive }

        override fun findActiveOverlapping(range: TimeRange): List<OwnershipRecord> = emptyList()
    }
}
