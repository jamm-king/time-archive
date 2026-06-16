package com.timearchive.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PurchaseReservationTest {
    @Test
    fun `creates held reservation with amount based on seconds`() {
        val now = Instant.parse("2026-06-16T00:00:00Z")
        val reservation = PurchaseReservation.held(
            id = UUID.randomUUID(),
            buyerId = UUID.randomUUID(),
            range = TimeRange(startSecond = 10, endSecond = 15),
            now = now,
            expiresAt = now.plusSeconds(600),
        )

        assertThat(reservation.amountCents).isEqualTo(500)
        assertThat(reservation.currency).isEqualTo("USD")
        assertThat(reservation.status).isEqualTo(PurchaseReservationStatus.HELD)
        assertThat(reservation.isActive).isTrue()
    }

    @Test
    fun `rejects reservation beyond canonical archive`() {
        val now = Instant.parse("2026-06-16T00:00:00Z")

        assertThatIllegalArgumentException()
            .isThrownBy {
                PurchaseReservation.held(
                    id = UUID.randomUUID(),
                    buyerId = UUID.randomUUID(),
                    range = TimeRange(startSecond = 86_399, endSecond = 86_401),
                    now = now,
                    expiresAt = now.plusSeconds(600),
                )
            }
            .withMessage("endSecond must be less than or equal to totalSeconds")
    }

    @Test
    fun `rejects expiration that is not after now`() {
        val now = Instant.parse("2026-06-16T00:00:00Z")

        assertThatIllegalArgumentException()
            .isThrownBy {
                PurchaseReservation.held(
                    id = UUID.randomUUID(),
                    buyerId = UUID.randomUUID(),
                    range = TimeRange(startSecond = 0, endSecond = 1),
                    now = now,
                    expiresAt = now,
                )
            }
            .withMessage("expiresAt must be after now")
    }

    @Test
    fun `detects expiration at a given instant`() {
        val now = Instant.parse("2026-06-16T00:00:00Z")
        val reservation = PurchaseReservation.held(
            id = UUID.randomUUID(),
            buyerId = UUID.randomUUID(),
            range = TimeRange(startSecond = 0, endSecond = 1),
            now = now,
            expiresAt = now.plusSeconds(600),
        )

        assertThat(reservation.isExpiredAt(now.plusSeconds(599))).isFalse()
        assertThat(reservation.isExpiredAt(now.plusSeconds(600))).isTrue()
    }
}
