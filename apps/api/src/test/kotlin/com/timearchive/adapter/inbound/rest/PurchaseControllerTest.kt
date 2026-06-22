package com.timearchive.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.timearchive.application.CreateCheckout
import com.timearchive.application.ReserveTimeRange
import com.timearchive.domain.model.CheckoutSession
import com.timearchive.domain.model.PurchaseReservation
import com.timearchive.domain.model.TimeRange
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class PurchaseControllerTest {
    private val reserveTimeRange: ReserveTimeRange = mockk()
    private val createCheckout: CreateCheckout = mockk()
    private val currentUserSession = CurrentUserSession()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(PurchaseController(reserveTimeRange, createCheckout, currentUserSession))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `creates reservation`() {
        val buyerId = UUID.randomUUID()
        val reservationId = UUID.randomUUID()
        val session = signedInSession(buyerId)
        val reservation = PurchaseReservation.held(
            id = reservationId,
            buyerId = buyerId,
            range = TimeRange(startSecond = 3_600, endSecond = 3_660),
            now = Instant.parse("2026-06-16T00:00:00Z"),
            expiresAt = Instant.parse("2026-06-16T00:10:00Z"),
        )
        every { reserveTimeRange.reserve(any()) } returns reservation

        mockMvc.post("/api/purchase/reservations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "startSecond" to 3_600,
                    "endSecond" to 3_660,
                ),
            )
            this.session = session
        }
            .andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.reservationId") { value(reservationId.toString()) }
                jsonPath("$.buyerId") { value(buyerId.toString()) }
                jsonPath("$.startSecond") { value(3_600) }
                jsonPath("$.endSecond") { value(3_660) }
                jsonPath("$.amountCents") { value(6_000) }
                jsonPath("$.currency") { value("USD") }
                jsonPath("$.status") { value("HELD") }
                jsonPath("$.expiresAt") { value("2026-06-16T00:10:00Z") }
            }

        verify {
            reserveTimeRange.reserve(
                ReserveTimeRange.Command(
                    buyerId = buyerId,
                    startSecond = 3_600,
                    endSecond = 3_660,
                ),
            )
        }
    }

    @Test
    fun `rejects invalid reservation request`() {
        val session = signedInSession(UUID.randomUUID())

        mockMvc.post("/api/purchase/reservations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "startSecond" to -1,
                    "endSecond" to 0,
                ),
            )
            this.session = session
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
                jsonPath("$.message") { value("Request validation failed") }
                jsonPath("$.details[*].field") { value(hasItem("startSecond")) }
                jsonPath("$.details[*].field") { value(hasItem("endSecond")) }
            }
    }

    @Test
    fun `maps active reservation overlap to conflict`() {
        val buyerId = UUID.randomUUID()
        val session = signedInSession(buyerId)
        every { reserveTimeRange.reserve(any()) } throws
            IllegalArgumentException("time range already has active reservation")

        mockMvc.post("/api/purchase/reservations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "startSecond" to 10,
                    "endSecond" to 20,
                ),
            )
            this.session = session
        }
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("TIME_RANGE_ALREADY_RESERVED") }
                jsonPath("$.message") { value("Time range already has active reservation") }
            }
    }

    @Test
    fun `creates checkout`() {
        val buyerId = UUID.randomUUID()
        val session = signedInSession(buyerId)
        val reservationId = UUID.randomUUID()
        every { createCheckout.create(any()) } returns CheckoutSession(
            provider = "fake",
            providerReference = "fake_checkout_$reservationId",
            checkoutUrl = "https://payments.example.test/checkout/$reservationId",
        )

        mockMvc.post("/api/purchase/reservations/{reservationId}/checkout", reservationId) {
            this.session = session
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.provider") { value("fake") }
                jsonPath("$.providerReference") { value("fake_checkout_$reservationId") }
                jsonPath("$.checkoutUrl") { value("https://payments.example.test/checkout/$reservationId") }
            }

        verify {
            createCheckout.create(
                CreateCheckout.Command(
                    currentUserId = buyerId,
                    reservationId = reservationId,
                ),
            )
        }
    }

    @Test
    fun `rejects malformed reservation id`() {
        mockMvc.post("/api/purchase/reservations/not-a-uuid/checkout") {
            this.session = signedInSession(UUID.randomUUID())
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
    }

    @Test
    fun `maps missing reservation to not found`() {
        every { createCheckout.create(any()) } throws IllegalStateException("purchase reservation not found")

        mockMvc.post("/api/purchase/reservations/{reservationId}/checkout", UUID.randomUUID()) {
            this.session = signedInSession(UUID.randomUUID())
        }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
                jsonPath("$.message") { value("Reservation was not found") }
            }
    }

    @Test
    fun `maps unavailable payment provider to service unavailable`() {
        every { createCheckout.create(any()) } throws IllegalStateException("payment provider is unavailable")

        mockMvc.post("/api/purchase/reservations/{reservationId}/checkout", UUID.randomUUID()) {
            this.session = signedInSession(UUID.randomUUID())
        }
            .andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("PAYMENT_PROVIDER_UNAVAILABLE") }
                jsonPath("$.message") { value("Payment provider is unavailable") }
            }
    }

    @Test
    fun `does not expose unexpected exception details`() {
        every { createCheckout.create(any()) } throws IllegalStateException("database password leaked")

        mockMvc.post("/api/purchase/reservations/{reservationId}/checkout", UUID.randomUUID()) {
            this.session = signedInSession(UUID.randomUUID())
        }
            .andExpect {
                status { isInternalServerError() }
                jsonPath("$.code") { value("UNEXPECTED_ERROR") }
                jsonPath("$.message") { value("Unexpected server error") }
                jsonPath("$.message") { value(containsString("Unexpected")) }
            }
    }

    @Test
    fun `rejects reservation without session`() {
        mockMvc.post("/api/purchase/reservations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "startSecond" to 10,
                    "endSecond" to 20,
                ),
            )
        }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            }
    }

    private fun signedInSession(userId: UUID): MockHttpSession =
        MockHttpSession().also { currentUserSession.signIn(it, userId) }
}
