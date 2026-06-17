package com.timearchive.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class OwnershipRecordTest {
    @Test
    fun `creates active ownership within canonical timeline`() {
        val now = Instant.parse("2026-06-16T00:00:00Z")
        val record = OwnershipRecord.active(
            id = UUID.randomUUID(),
            range = TimeRange(startSecond = 0, endSecond = 1),
            ownerId = UUID.randomUUID(),
            validFrom = now,
            acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
        )

        assertThat(record.isActive).isTrue()
        assertThat(record.validUntil).isNull()
        assertThat(record.createdAt).isEqualTo(now)
        assertThat(record.updatedAt).isEqualTo(now)
    }

    @Test
    fun `rejects active ownership beyond canonical timeline`() {
        assertThatIllegalArgumentException()
            .isThrownBy {
                OwnershipRecord.active(
                    id = UUID.randomUUID(),
                    range = TimeRange(startSecond = 86_399, endSecond = 86_401),
                    ownerId = UUID.randomUUID(),
                    validFrom = Instant.parse("2026-06-16T00:00:00Z"),
                    acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
                )
            }
            .withMessage("endSecond must be less than or equal to totalSeconds")
    }

    @Test
    fun `rejects active ownership with validUntil`() {
        val now = Instant.parse("2026-06-16T00:00:00Z")

        assertThatIllegalArgumentException()
            .isThrownBy {
                OwnershipRecord(
                    id = UUID.randomUUID(),
                    range = TimeRange(startSecond = 0, endSecond = 1),
                    ownerId = UUID.randomUUID(),
                    status = OwnershipStatus.ACTIVE,
                    validFrom = now,
                    validUntil = now.plusSeconds(1),
                    acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
                    sourcePurchaseId = null,
                    sourceTransactionId = null,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            .withMessage("active ownership must not have validUntil")
    }
}
