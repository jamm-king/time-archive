package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CheckAvailability
import com.timearchive.domain.model.TimeRange
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AvailabilityControllerTest {
    private val checkAvailability: CheckAvailability = mockk()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AvailabilityController(checkAvailability))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `returns available response`() {
        every { checkAvailability.check(any()) } returns CheckAvailability.Result(
            range = TimeRange(startSecond = 0, endSecond = 10),
            available = true,
            conflicts = emptyList(),
        )

        mockMvc.get("/api/archive/availability") {
            param("startSecond", "0")
            param("endSecond", "10")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.startSecond") { value(0) }
                jsonPath("$.endSecond") { value(10) }
                jsonPath("$.available") { value(true) }
                jsonPath("$.conflicts.length()") { value(0) }
            }

        verify {
            checkAvailability.check(CheckAvailability.Query(startSecond = 0, endSecond = 10))
        }
    }

    @Test
    fun `returns unavailable response with conflict summaries only`() {
        every { checkAvailability.check(any()) } returns CheckAvailability.Result(
            range = TimeRange(startSecond = 10, endSecond = 20),
            available = false,
            conflicts = listOf(
                CheckAvailability.Conflict(
                    type = CheckAvailability.ConflictType.RESERVATION,
                    range = TimeRange(startSecond = 12, endSecond = 18),
                ),
            ),
        )

        mockMvc.get("/api/archive/availability") {
            param("startSecond", "10")
            param("endSecond", "20")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.available") { value(false) }
                jsonPath("$.conflicts[0].type") { value("RESERVATION") }
                jsonPath("$.conflicts[0].startSecond") { value(12) }
                jsonPath("$.conflicts[0].endSecond") { value(18) }
                jsonPath("$.conflicts[0].reservationId") { doesNotExist() }
                jsonPath("$.conflicts[0].buyerId") { doesNotExist() }
                jsonPath("$.conflicts[0].ownerId") { doesNotExist() }
            }
    }

    @Test
    fun `rejects missing query parameter`() {
        mockMvc.get("/api/archive/availability") {
            param("startSecond", "0")
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
    }

    @Test
    fun `rejects malformed query parameter`() {
        mockMvc.get("/api/archive/availability") {
            param("startSecond", "not-a-number")
            param("endSecond", "10")
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
    }

    @Test
    fun `rejects out of range query parameter`() {
        every { checkAvailability.check(any()) } throws
            IllegalArgumentException("startSecond must be greater than or equal to 0")

        mockMvc.get("/api/archive/availability") {
            param("startSecond", "-1")
            param("endSecond", "10")
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
                jsonPath("$.message") { value("Invalid request") }
            }
    }

    @Test
    fun `maps invalid range ordering to invalid request`() {
        every { checkAvailability.check(any()) } throws
            IllegalArgumentException("endSecond must be greater than startSecond")

        mockMvc.get("/api/archive/availability") {
            param("startSecond", "10")
            param("endSecond", "10")
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
                jsonPath("$.message") { value("Invalid request") }
                jsonPath("$.message") { value(not("endSecond must be greater than startSecond")) }
            }
    }
}
