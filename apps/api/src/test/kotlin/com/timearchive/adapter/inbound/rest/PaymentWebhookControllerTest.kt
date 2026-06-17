package com.timearchive.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.timearchive.application.CompletePrimaryPurchase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class PaymentWebhookControllerTest {
    private val completePrimaryPurchase: CompletePrimaryPurchase = mockk()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(PaymentWebhookController(completePrimaryPurchase))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `completes fake primary purchase payment`() {
        val reservationId = UUID.randomUUID()
        val purchaseId = UUID.randomUUID()
        val ownershipRecordId = UUID.randomUUID()
        every { completePrimaryPurchase.complete(any()) } returns CompletePrimaryPurchase.Result(
            purchaseId = purchaseId,
            ownershipRecordId = ownershipRecordId,
            alreadyProcessed = false,
        )

        mockMvc.post("/api/internal/payments/fake/webhooks/primary-purchase-completed") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody(reservationId = reservationId))
        }
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.purchaseId") { value(purchaseId.toString()) }
                jsonPath("$.ownershipRecordId") { value(ownershipRecordId.toString()) }
                jsonPath("$.alreadyProcessed") { value(false) }
            }

        verify {
            completePrimaryPurchase.complete(
                CompletePrimaryPurchase.Command(
                    provider = "fake",
                    providerEventId = "evt_local_1",
                    eventType = "payment_intent.succeeded",
                    payloadHash = "sha256-local-test-payload",
                    reservationId = reservationId,
                    paymentReference = "pi_local_1",
                    requestId = "local-request-1",
                ),
            )
        }
    }

    @Test
    fun `returns already processed duplicate event response`() {
        val purchaseId = UUID.randomUUID()
        every { completePrimaryPurchase.complete(any()) } returns CompletePrimaryPurchase.Result(
            purchaseId = purchaseId,
            ownershipRecordId = null,
            alreadyProcessed = true,
        )

        mockMvc.post("/api/internal/payments/fake/webhooks/primary-purchase-completed") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody())
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.purchaseId") { value(purchaseId.toString()) }
                jsonPath("$.ownershipRecordId") { doesNotExist() }
                jsonPath("$.alreadyProcessed") { value(true) }
            }
    }

    @Test
    fun `rejects invalid request body`() {
        mockMvc.post("/api/internal/payments/fake/webhooks/primary-purchase-completed") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "providerEventId" to "",
                    "eventType" to "",
                    "payloadHash" to "",
                    "paymentReference" to "",
                ),
            )
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
                jsonPath("$.message") { value("Request validation failed") }
            }
    }

    @Test
    fun `maps missing reservation to not found`() {
        every { completePrimaryPurchase.complete(any()) } throws IllegalStateException("purchase reservation not found")

        mockMvc.post("/api/internal/payments/fake/webhooks/primary-purchase-completed") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody())
        }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
                jsonPath("$.message") { value("Reservation was not found") }
            }
    }

    @Test
    fun `maps expired reservation to conflict`() {
        every { completePrimaryPurchase.complete(any()) } throws IllegalArgumentException("reservation is expired")

        mockMvc.post("/api/internal/payments/fake/webhooks/primary-purchase-completed") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody())
        }
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("RESERVATION_EXPIRED") }
                jsonPath("$.message") { value("Reservation is expired") }
            }
    }

    @Test
    fun `maps non payable reservation to conflict`() {
        every { completePrimaryPurchase.complete(any()) } throws IllegalArgumentException("reservation is not payable")

        mockMvc.post("/api/internal/payments/fake/webhooks/primary-purchase-completed") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody())
        }
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("RESERVATION_NOT_PAYABLE") }
                jsonPath("$.message") { value("Reservation is not payable") }
            }
    }

    @Test
    fun `maps ownership conflict to conflict`() {
        every { completePrimaryPurchase.complete(any()) } throws
            IllegalArgumentException("time range already has active ownership")

        mockMvc.post("/api/internal/payments/fake/webhooks/primary-purchase-completed") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody())
        }
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("TIME_RANGE_ALREADY_OWNED") }
                jsonPath("$.message") { value("Time range already has active ownership") }
            }
    }

    @Test
    fun `maps duplicate in progress payment event to conflict`() {
        every { completePrimaryPurchase.complete(any()) } throws
            IllegalArgumentException("payment event is already being processed")

        mockMvc.post("/api/internal/payments/fake/webhooks/primary-purchase-completed") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody())
        }
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("PAYMENT_EVENT_ALREADY_PROCESSING") }
                jsonPath("$.message") { value("Payment event is already being processed") }
            }
    }

    @Test
    fun `does not expose unexpected exception details`() {
        every { completePrimaryPurchase.complete(any()) } throws IllegalStateException("database password leaked")

        mockMvc.post("/api/internal/payments/fake/webhooks/primary-purchase-completed") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody())
        }
            .andExpect {
                status { isInternalServerError() }
                jsonPath("$.code") { value("UNEXPECTED_ERROR") }
                jsonPath("$.message") { value("Unexpected server error") }
                jsonPath("$.message") { value(containsString("Unexpected")) }
            }
    }

    private fun requestBody(
        reservationId: UUID = UUID.randomUUID(),
    ): Map<String, Any> =
        mapOf(
            "providerEventId" to "evt_local_1",
            "eventType" to "payment_intent.succeeded",
            "payloadHash" to "sha256-local-test-payload",
            "reservationId" to reservationId,
            "paymentReference" to "pi_local_1",
            "requestId" to "local-request-1",
        )
}
