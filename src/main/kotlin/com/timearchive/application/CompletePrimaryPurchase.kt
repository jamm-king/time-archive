package com.timearchive.application

import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.AuditLog
import com.timearchive.domain.model.OutboxEvent
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.PaymentEvent
import com.timearchive.domain.model.Purchase
import com.timearchive.domain.model.PurchaseReservationStatus
import com.timearchive.domain.port.AuditLogPort
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.OutboxPort
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.PaymentEventRepository
import com.timearchive.domain.port.PurchaseRepository
import com.timearchive.domain.port.PurchaseReservationRepository
import com.timearchive.domain.port.TransactionPort
import java.util.UUID

class CompletePrimaryPurchase(
    private val transactionPort: TransactionPort,
    private val purchaseReservationRepository: PurchaseReservationRepository,
    private val purchaseRepository: PurchaseRepository,
    private val paymentEventRepository: PaymentEventRepository,
    private val ownershipRepository: OwnershipRepository,
    private val auditLogPort: AuditLogPort,
    private val outboxPort: OutboxPort,
    private val clockPort: ClockPort,
    private val idGenerator: () -> UUID = UUID::randomUUID,
) {
    fun complete(command: Command): Result =
        transactionPort.execute {
            val now = clockPort.now()
            val existingEvent = paymentEventRepository.findByProviderAndEventId(
                provider = command.provider,
                providerEventId = command.providerEventId,
            )

            if (existingEvent?.isProcessed == true) {
                val purchase = purchaseRepository.findByReservationId(command.reservationId)
                    ?: error("processed payment event has no purchase")
                return@execute Result(
                    purchaseId = purchase.id,
                    ownershipRecordId = null,
                    alreadyProcessed = true,
                )
            }

            require(existingEvent == null) { "payment event is already being processed" }

            paymentEventRepository.save(
                PaymentEvent.received(
                    id = idGenerator(),
                    provider = command.provider,
                    providerEventId = command.providerEventId,
                    eventType = command.eventType,
                    payloadHash = command.payloadHash,
                    receivedAt = now,
                ),
            )

            val reservation = purchaseReservationRepository.findByIdForUpdate(command.reservationId)
                ?: error("purchase reservation not found")

            require(!reservation.isExpiredAt(now)) { "reservation is expired" }
            require(
                reservation.status == PurchaseReservationStatus.HELD ||
                    reservation.status == PurchaseReservationStatus.CHECKOUT_CREATED,
            ) {
                "reservation is not payable"
            }
            require(ownershipRepository.findActiveOverlapping(reservation.range).isEmpty()) {
                "time range already has active ownership"
            }

            val purchase = purchaseRepository.save(
                Purchase.ownershipGranted(
                    id = idGenerator(),
                    reservation = reservation,
                    paymentProvider = command.provider,
                    paymentReference = command.paymentReference,
                    now = now,
                ),
            )
            val ownershipRecord = ownershipRepository.save(
                OwnershipRecord.active(
                    id = idGenerator(),
                    range = reservation.range,
                    ownerId = reservation.buyerId,
                    validFrom = now,
                    acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
                    sourcePurchaseId = purchase.id,
                ),
            )

            val completedCount = purchaseReservationRepository.markCompleted(reservation.id, now)
            require(completedCount == 1) { "reservation completion failed" }

            paymentEventRepository.markProcessed(
                provider = command.provider,
                providerEventId = command.providerEventId,
                processedAt = now,
            )

            appendAuditLogs(command, purchase, ownershipRecord)
            appendOutboxEvents(purchase, ownershipRecord)

            Result(
                purchaseId = purchase.id,
                ownershipRecordId = ownershipRecord.id,
                alreadyProcessed = false,
            )
        }

    private fun appendAuditLogs(
        command: Command,
        purchase: Purchase,
        ownershipRecord: OwnershipRecord,
    ) {
        val now = clockPort.now()
        auditLogPort.append(
            AuditLog(
                id = idGenerator(),
                actorUserId = purchase.buyerId,
                actorType = "USER",
                action = "PRIMARY_PURCHASE_COMPLETED",
                resourceType = "PURCHASE",
                resourceId = purchase.id,
                beforeState = null,
                afterState = """{"status":"${purchase.status.name}"}""",
                requestId = command.requestId,
                createdAt = now,
            ),
        )
        auditLogPort.append(
            AuditLog(
                id = idGenerator(),
                actorUserId = purchase.buyerId,
                actorType = "USER",
                action = "OWNERSHIP_CREATED",
                resourceType = "OWNERSHIP_RECORD",
                resourceId = ownershipRecord.id,
                beforeState = null,
                afterState = """{"status":"${ownershipRecord.status.name}"}""",
                requestId = command.requestId,
                createdAt = now,
            ),
        )
    }

    private fun appendOutboxEvents(
        purchase: Purchase,
        ownershipRecord: OwnershipRecord,
    ) {
        val now = clockPort.now()
        outboxPort.append(
            OutboxEvent(
                id = idGenerator(),
                eventType = "PurchaseCompleted",
                aggregateType = "Purchase",
                aggregateId = purchase.id,
                payload = """{"purchaseId":"${purchase.id}"}""",
                status = OutboxEvent.PENDING,
                createdAt = now,
                processedAt = null,
                retryCount = 0,
                lastError = null,
            ),
        )
        outboxPort.append(
            OutboxEvent(
                id = idGenerator(),
                eventType = "OwnershipCreated",
                aggregateType = "OwnershipRecord",
                aggregateId = ownershipRecord.id,
                payload = """{"ownershipRecordId":"${ownershipRecord.id}"}""",
                status = OutboxEvent.PENDING,
                createdAt = now,
                processedAt = null,
                retryCount = 0,
                lastError = null,
            ),
        )
        outboxPort.append(
            OutboxEvent(
                id = idGenerator(),
                eventType = "TimelineManifestInvalidated",
                aggregateType = "OwnershipRecord",
                aggregateId = ownershipRecord.id,
                payload = """{"startSecond":${ownershipRecord.range.startSecond},"endSecond":${ownershipRecord.range.endSecond}}""",
                status = OutboxEvent.PENDING,
                createdAt = now,
                processedAt = null,
                retryCount = 0,
                lastError = null,
            ),
        )
    }

    data class Command(
        val provider: String,
        val providerEventId: String,
        val eventType: String,
        val payloadHash: String,
        val reservationId: UUID,
        val paymentReference: String,
        val requestId: String?,
    )

    data class Result(
        val purchaseId: UUID,
        val ownershipRecordId: UUID?,
        val alreadyProcessed: Boolean,
    )
}
