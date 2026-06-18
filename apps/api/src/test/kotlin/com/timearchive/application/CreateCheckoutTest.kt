package com.timearchive.application

import com.timearchive.domain.model.CheckoutRequest
import com.timearchive.domain.model.CheckoutSession
import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.PaymentPort
import com.timearchive.domain.port.PurchaseReservationRepository
import com.timearchive.domain.port.TransactionPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CreateCheckoutTest {
    private val now: Instant = Instant.parse("2026-06-16T00:00:00Z")

    @Test
    fun `creates checkout and marks reservation checkout created`() {
        val reservation = heldReservation()
        val repository = FakePurchaseReservationRepository(reservation)
        val paymentPort = FakePaymentPort()
        val useCase = useCase(repository = repository, paymentPort = paymentPort)

        val checkout = useCase.create(commandFor(reservation))

        assertThat(checkout.provider).isEqualTo("fake")
        assertThat(checkout.providerReference).isEqualTo("checkout-${reservation.id}")
        assertThat(repository.checkoutCreatedIds).containsExactly(reservation.id)
        assertThat(paymentPort.requests).hasSize(1)
        assertThat(paymentPort.requests.first().amountCents).isEqualTo(reservation.amountCents)
    }

    @Test
    fun `rejects missing reservation`() {
        val useCase = useCase(repository = FakePurchaseReservationRepository(null))

        assertThatIllegalStateException()
            .isThrownBy {
                useCase.create(
                    CreateCheckout.Command(
                        currentUserId = UUID.randomUUID(),
                        reservationId = UUID.randomUUID(),
                    ),
                )
            }
            .withMessage("purchase reservation not found")
    }

    @Test
    fun `rejects checkout by user who does not own reservation`() {
        val reservation = heldReservation()
        val paymentPort = FakePaymentPort()
        val repository = FakePurchaseReservationRepository(reservation)
        val useCase = useCase(repository = repository, paymentPort = paymentPort)

        assertThatIllegalArgumentException()
            .isThrownBy {
                useCase.create(
                    CreateCheckout.Command(
                        currentUserId = UUID.randomUUID(),
                        reservationId = reservation.id,
                    ),
                )
            }
            .withMessage("reservation is not owned by current user")

        assertThat(paymentPort.requests).isEmpty()
        assertThat(repository.checkoutCreatedIds).isEmpty()
    }

    @Test
    fun `rejects expired reservation`() {
        val reservation = heldReservation(
            createdAt = now.minusSeconds(1_200),
            expiresAt = now.minusSeconds(600),
        )
        val paymentPort = FakePaymentPort()
        val repository = FakePurchaseReservationRepository(reservation)
        val useCase = useCase(repository = repository, paymentPort = paymentPort)

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.create(commandFor(reservation)) }
            .withMessage("reservation is expired")

        assertThat(paymentPort.requests).isEmpty()
        assertThat(repository.checkoutCreatedIds).isEmpty()
    }

    @Test
    fun `rejects reservation that is already checkout created`() {
        val reservation = reservationWithStatus(PurchaseReservationStatus.CHECKOUT_CREATED)
        val useCase = useCase(repository = FakePurchaseReservationRepository(reservation))

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.create(commandFor(reservation)) }
            .withMessage("reservation is not held")
    }

    @Test
    fun `does not mark reservation when payment port fails`() {
        val reservation = heldReservation()
        val repository = FakePurchaseReservationRepository(reservation)
        val useCase = useCase(
            repository = repository,
            paymentPort = FailingPaymentPort,
        )

        assertThatIllegalStateException()
            .isThrownBy { useCase.create(commandFor(reservation)) }
            .withMessage("payment provider unavailable")

        assertThat(repository.checkoutCreatedIds).isEmpty()
    }

    private fun useCase(
        repository: PurchaseReservationRepository,
        paymentPort: PaymentPort = FakePaymentPort(),
    ): CreateCheckout =
        CreateCheckout(
            transactionPort = ImmediateTransactionPort,
            purchaseReservationRepository = repository,
            paymentPort = paymentPort,
            clockPort = ClockPort { now },
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

    private fun reservationWithStatus(status: PurchaseReservationStatus): PurchaseReservation {
        val held = heldReservation()
        return held.copy(status = status)
    }

    private fun commandFor(reservation: PurchaseReservation): CreateCheckout.Command =
        CreateCheckout.Command(
            currentUserId = reservation.buyerId,
            reservationId = reservation.id,
        )

    private object ImmediateTransactionPort : TransactionPort {
        override fun <T> execute(block: () -> T): T = block()
    }

    private class FakePurchaseReservationRepository(
        private val reservation: PurchaseReservation?,
    ) : PurchaseReservationRepository {
        val checkoutCreatedIds = mutableListOf<UUID>()

        override fun save(reservation: PurchaseReservation): PurchaseReservation = reservation

        override fun findByIdForUpdate(id: UUID): PurchaseReservation? = reservation?.takeIf { it.id == id }

        override fun findActiveOverlapping(range: TimeRange): List<PurchaseReservation> = emptyList()

        override fun expireOverdue(now: Instant): Int = 0

        override fun markCheckoutCreated(
            id: UUID,
            now: Instant,
        ): Int {
            checkoutCreatedIds.add(id)
            return 1
        }

        override fun markCompleted(
            id: UUID,
            now: Instant,
        ): Int = 0
    }

    private class FakePaymentPort : PaymentPort {
        val requests = mutableListOf<CheckoutRequest>()

        override fun createCheckout(request: CheckoutRequest): CheckoutSession {
            requests.add(request)
            return CheckoutSession(
                provider = "fake",
                providerReference = "checkout-${request.reservationId}",
                checkoutUrl = "https://payments.example.test/checkout/${request.reservationId}",
            )
        }
    }

    private object FailingPaymentPort : PaymentPort {
        override fun createCheckout(request: CheckoutRequest): CheckoutSession =
            error("payment provider unavailable")
    }
}
