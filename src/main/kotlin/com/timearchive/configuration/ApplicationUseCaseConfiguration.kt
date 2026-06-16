package com.timearchive.configuration

import com.timearchive.application.CompletePrimaryPurchase
import com.timearchive.application.CreateCheckout
import com.timearchive.application.CheckAvailability
import com.timearchive.application.ReserveTimeRange
import com.timearchive.domain.port.AuditLogPort
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.OutboxPort
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.PaymentEventRepository
import com.timearchive.domain.port.PaymentPort
import com.timearchive.domain.port.PurchaseRepository
import com.timearchive.domain.port.PurchaseReservationRepository
import com.timearchive.domain.port.TransactionPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant

@Configuration
class ApplicationUseCaseConfiguration {
    @Bean
    fun clockPort(): ClockPort = ClockPort { Instant.now() }

    @Bean
    fun reserveTimeRange(
        ownershipRepository: OwnershipRepository,
        purchaseReservationRepository: PurchaseReservationRepository,
        clockPort: ClockPort,
    ): ReserveTimeRange =
        ReserveTimeRange(
            ownershipRepository = ownershipRepository,
            purchaseReservationRepository = purchaseReservationRepository,
            clockPort = clockPort,
        )

    @Bean
    fun checkAvailability(
        ownershipRepository: OwnershipRepository,
        purchaseReservationRepository: PurchaseReservationRepository,
        clockPort: ClockPort,
    ): CheckAvailability =
        CheckAvailability(
            ownershipRepository = ownershipRepository,
            purchaseReservationRepository = purchaseReservationRepository,
            clockPort = clockPort,
        )

    @Bean
    fun createCheckout(
        transactionPort: TransactionPort,
        purchaseReservationRepository: PurchaseReservationRepository,
        paymentPort: PaymentPort,
        clockPort: ClockPort,
    ): CreateCheckout =
        CreateCheckout(
            transactionPort = transactionPort,
            purchaseReservationRepository = purchaseReservationRepository,
            paymentPort = paymentPort,
            clockPort = clockPort,
        )

    @Bean
    fun completePrimaryPurchase(
        transactionPort: TransactionPort,
        purchaseReservationRepository: PurchaseReservationRepository,
        purchaseRepository: PurchaseRepository,
        paymentEventRepository: PaymentEventRepository,
        ownershipRepository: OwnershipRepository,
        auditLogPort: AuditLogPort,
        outboxPort: OutboxPort,
        clockPort: ClockPort,
    ): CompletePrimaryPurchase =
        CompletePrimaryPurchase(
            transactionPort = transactionPort,
            purchaseReservationRepository = purchaseReservationRepository,
            purchaseRepository = purchaseRepository,
            paymentEventRepository = paymentEventRepository,
            ownershipRepository = ownershipRepository,
            auditLogPort = auditLogPort,
            outboxPort = outboxPort,
            clockPort = clockPort,
        )
}
