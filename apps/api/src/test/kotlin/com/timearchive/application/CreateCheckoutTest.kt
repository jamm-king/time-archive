package com.timearchive.application

import com.timearchive.domain.model.CheckoutAttempt
import com.timearchive.domain.model.CheckoutAttemptStatus
import com.timearchive.domain.model.CheckoutRequest
import com.timearchive.domain.model.CheckoutSession
import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.CheckoutAttemptRepository
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
        val checkoutAttemptRepository = FakeCheckoutAttemptRepository()
        val paymentPort = FakePaymentPort()
        val useCase = useCase(
            reservationRepository = repository,
            checkoutAttemptRepository = checkoutAttemptRepository,
            paymentPort = paymentPort,
        )

        val checkout = useCase.create(commandFor(reservation))

        assertThat(checkout.provider).isEqualTo("fake")
        assertThat(checkout.providerReference).isEqualTo("checkout-${reservation.id}")
        assertThat(repository.checkoutCreatedIds).containsExactly(reservation.id)
        assertThat(checkoutAttemptRepository.savedAttempts).hasSize(1)
        assertThat(checkoutAttemptRepository.providerCreated).containsExactly(checkoutAttemptRepository.savedAttempts.single().id)
        assertThat(paymentPort.requests).hasSize(1)
        assertThat(paymentPort.requests.first().amountCents).isEqualTo(reservation.amountCents)
        assertThat(paymentPort.requests.first().providerRequestId).isEqualTo(
            checkoutAttemptRepository.savedAttempts.single().providerRequestId,
        )
    }

    @Test
    fun `returns existing provider checkout without calling payment provider`() {
        val reservation = reservationWithStatus(PurchaseReservationStatus.CHECKOUT_CREATED)
        val existingAttempt = checkoutAttemptFor(reservation)
        val checkoutAttemptRepository = FakeCheckoutAttemptRepository(existingAttempt)
        val paymentPort = FakePaymentPort()
        val useCase = useCase(
            reservationRepository = FakePurchaseReservationRepository(reservation),
            checkoutAttemptRepository = checkoutAttemptRepository,
            paymentPort = paymentPort,
        )

        val checkout = useCase.create(commandFor(reservation))

        assertThat(checkout.provider).isEqualTo("fake")
        assertThat(checkout.providerReference).isEqualTo("provider-order-${reservation.id}")
        assertThat(checkout.checkoutUrl).isEqualTo("https://payments.example.test/existing/${reservation.id}")
        assertThat(paymentPort.requests).isEmpty()
        assertThat(checkoutAttemptRepository.providerCreated).isEmpty()
    }

    @Test
    fun `rejects missing reservation`() {
        val useCase = useCase(reservationRepository = FakePurchaseReservationRepository(null))

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
        val checkoutAttemptRepository = FakeCheckoutAttemptRepository()
        val useCase = useCase(
            reservationRepository = repository,
            checkoutAttemptRepository = checkoutAttemptRepository,
            paymentPort = paymentPort,
        )

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
        assertThat(checkoutAttemptRepository.savedAttempts).isEmpty()
    }

    @Test
    fun `rejects expired reservation`() {
        val reservation = heldReservation(
            createdAt = now.minusSeconds(1_200),
            expiresAt = now.minusSeconds(600),
        )
        val paymentPort = FakePaymentPort()
        val repository = FakePurchaseReservationRepository(reservation)
        val checkoutAttemptRepository = FakeCheckoutAttemptRepository()
        val useCase = useCase(
            reservationRepository = repository,
            checkoutAttemptRepository = checkoutAttemptRepository,
            paymentPort = paymentPort,
        )

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.create(commandFor(reservation)) }
            .withMessage("reservation is expired")

        assertThat(paymentPort.requests).isEmpty()
        assertThat(repository.checkoutCreatedIds).isEmpty()
        assertThat(checkoutAttemptRepository.savedAttempts).isEmpty()
    }

    @Test
    fun `rejects reservation that is already completed`() {
        val reservation = reservationWithStatus(PurchaseReservationStatus.COMPLETED)
        val useCase = useCase(reservationRepository = FakePurchaseReservationRepository(reservation))

        assertThatIllegalArgumentException()
            .isThrownBy { useCase.create(commandFor(reservation)) }
            .withMessage("reservation is not checkout eligible")
    }

    @Test
    fun `marks checkout attempt failed when payment port fails`() {
        val reservation = heldReservation()
        val repository = FakePurchaseReservationRepository(reservation)
        val checkoutAttemptRepository = FakeCheckoutAttemptRepository()
        val useCase = useCase(
            reservationRepository = repository,
            checkoutAttemptRepository = checkoutAttemptRepository,
            paymentPort = FailingPaymentPort,
        )

        assertThatIllegalStateException()
            .isThrownBy { useCase.create(commandFor(reservation)) }
            .withMessage("payment provider unavailable")

        assertThat(repository.checkoutCreatedIds).containsExactly(reservation.id)
        assertThat(checkoutAttemptRepository.providerFailed).containsExactly(checkoutAttemptRepository.savedAttempts.single().id)
    }

    private fun useCase(
        reservationRepository: PurchaseReservationRepository,
        checkoutAttemptRepository: CheckoutAttemptRepository = FakeCheckoutAttemptRepository(),
        paymentPort: PaymentPort = FakePaymentPort(),
    ): CreateCheckout =
        CreateCheckout(
            transactionPort = ImmediateTransactionPort,
            purchaseReservationRepository = reservationRepository,
            checkoutAttemptRepository = checkoutAttemptRepository,
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

    private fun checkoutAttemptFor(reservation: PurchaseReservation): CheckoutAttempt =
        CheckoutAttempt(
            id = UUID.randomUUID(),
            reservationId = reservation.id,
            buyerId = reservation.buyerId,
            provider = "fake",
            providerRequestId = UUID.randomUUID().toString(),
            providerReference = "provider-order-${reservation.id}",
            checkoutUrl = "https://payments.example.test/existing/${reservation.id}",
            status = CheckoutAttemptStatus.PROVIDER_CREATED,
            createdAt = now,
            updatedAt = now,
        )

    private fun commandFor(reservation: PurchaseReservation): CreateCheckout.Command =
        CreateCheckout.Command(
            currentUserId = reservation.buyerId,
            reservationId = reservation.id,
        )

    private object ImmediateTransactionPort : TransactionPort {
        override fun <T> execute(block: () -> T): T = block()
    }

    private class FakeCheckoutAttemptRepository(
        private var attempt: CheckoutAttempt? = null,
    ) : CheckoutAttemptRepository {
        val savedAttempts = mutableListOf<CheckoutAttempt>()
        val providerCreated = mutableListOf<UUID>()
        val providerFailed = mutableListOf<UUID>()

        override fun save(attempt: CheckoutAttempt): CheckoutAttempt {
            this.attempt = attempt
            savedAttempts.add(attempt)
            return attempt
        }

        override fun findByReservationIdForUpdate(reservationId: UUID): CheckoutAttempt? =
            attempt?.takeIf { it.reservationId == reservationId }

        override fun markProviderCreated(
            id: UUID,
            providerReference: String,
            checkoutUrl: String,
            now: Instant,
        ): Int {
            providerCreated.add(id)
            attempt = attempt?.copy(
                providerReference = providerReference,
                checkoutUrl = checkoutUrl,
                status = CheckoutAttemptStatus.PROVIDER_CREATED,
                updatedAt = now,
            )
            return 1
        }

        override fun markProviderFailed(
            id: UUID,
            now: Instant,
        ): Int {
            providerFailed.add(id)
            attempt = attempt?.copy(
                status = CheckoutAttemptStatus.PROVIDER_FAILED,
                updatedAt = now,
            )
            return 1
        }
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
        override val provider: String = "fake"

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
        override val provider: String = "fake"

        override fun createCheckout(request: CheckoutRequest): CheckoutSession =
            error("payment provider unavailable")
    }
}
