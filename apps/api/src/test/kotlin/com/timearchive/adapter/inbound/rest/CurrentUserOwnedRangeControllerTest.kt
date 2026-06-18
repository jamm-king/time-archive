package com.timearchive.adapter.inbound.rest

import com.timearchive.application.ListCurrentUserOwnedRanges
import com.timearchive.domain.model.AcquisitionType
import com.timearchive.domain.model.OwnershipRecord
import com.timearchive.domain.model.TimeRange
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class CurrentUserOwnedRangeControllerTest {
    private val listCurrentUserOwnedRanges: ListCurrentUserOwnedRanges = mockk()
    private val currentUserSession = CurrentUserSession()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                CurrentUserOwnedRangeController(
                    listCurrentUserOwnedRanges = listCurrentUserOwnedRanges,
                    currentUserSession = currentUserSession,
                ),
            )
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `lists current user's owned ranges`() {
        val currentUserId = UUID.fromString("00000000-0000-0000-0000-000000000901")
        val session = MockHttpSession()
        val record = activeOwnership(ownerId = currentUserId)
        currentUserSession.signIn(session, currentUserId)
        every { listCurrentUserOwnedRanges.list(any()) } returns listOf(record)

        mockMvc.get("/api/me/owned-ranges") {
            this.session = session
        }
            .andExpect {
                status { isOk() }
                jsonPath("$[0].ownershipRecordId") { value(record.id.toString()) }
                jsonPath("$[0].startSecond") { value(10) }
                jsonPath("$[0].endSecond") { value(12) }
                jsonPath("$[0].status") { value("ACTIVE") }
                jsonPath("$[0].acquiredAt") { value("2026-06-18T00:00:00Z") }
                jsonPath("$[0].ownerId") { doesNotExist() }
            }

        verify {
            listCurrentUserOwnedRanges.list(
                ListCurrentUserOwnedRanges.Query(currentUserId = currentUserId),
            )
        }
    }

    @Test
    fun `rejects owned range list request without session`() {
        mockMvc.get("/api/me/owned-ranges")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            }
    }

    private fun activeOwnership(ownerId: UUID): OwnershipRecord =
        OwnershipRecord.active(
            id = UUID.fromString("00000000-0000-0000-0000-000000000902"),
            range = TimeRange(startSecond = 10, endSecond = 12),
            ownerId = ownerId,
            validFrom = Instant.parse("2026-06-18T00:00:00Z"),
            acquisitionType = AcquisitionType.PRIMARY_PURCHASE,
        )
}
