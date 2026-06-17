package com.timearchive.adapter.inbound.rest

import com.timearchive.application.ListPublicTimelineSegments
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.PublicTimelineSegment
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
import java.util.UUID

class PublicTimelineControllerTest {
    private val listPublicTimelineSegments: ListPublicTimelineSegments = mockk()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(PublicTimelineController(listPublicTimelineSegments))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `returns public timeline segments`() {
        every { listPublicTimelineSegments.list(any()) } returns ListPublicTimelineSegments.Result(
            range = TimeRange(startSecond = 0, endSecond = 300),
            segments = listOf(segment()),
        )

        mockMvc.get("/api/timeline") {
            param("from", "0")
            param("to", "300")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.from") { value(0) }
                jsonPath("$.to") { value(300) }
                jsonPath("$.segments[0].startSecond") { value(10) }
                jsonPath("$.segments[0].endSecond") { value(20) }
                jsonPath("$.segments[0].mediaAssetId") {
                    value("00000000-0000-0000-0000-000000000901")
                }
                jsonPath("$.segments[0].mediaType") { value("VIDEO") }
                jsonPath("$.segments[0].mediaUrl") { value("https://cdn.example.com/approved.mp4") }
                jsonPath("$.segments[0].thumbnailUrl") { value("https://cdn.example.com/thumb.jpg") }
                jsonPath("$.segments[0].externalLink") { value("https://example.com") }
                jsonPath("$.segments[0].ownerId") { doesNotExist() }
                jsonPath("$.segments[0].originalFileUrl") { doesNotExist() }
                jsonPath("$.segments[0].moderationStatus") { doesNotExist() }
            }

        verify {
            listPublicTimelineSegments.list(ListPublicTimelineSegments.Query(from = 0, to = 300))
        }
    }

    @Test
    fun `rejects missing query parameter`() {
        mockMvc.get("/api/timeline") {
            param("from", "0")
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
    }

    @Test
    fun `rejects malformed query parameter`() {
        mockMvc.get("/api/timeline") {
            param("from", "not-a-number")
            param("to", "300")
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
    }

    @Test
    fun `maps invalid range ordering to invalid request`() {
        every { listPublicTimelineSegments.list(any()) } throws
            IllegalArgumentException("endSecond must be greater than startSecond")

        mockMvc.get("/api/timeline") {
            param("from", "20")
            param("to", "20")
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
                jsonPath("$.message") { value("Invalid request") }
                jsonPath("$.message") { value(not("endSecond must be greater than startSecond")) }
            }
    }

    private fun segment(): PublicTimelineSegment =
        PublicTimelineSegment(
            range = TimeRange(startSecond = 10, endSecond = 20),
            mediaAssetId = UUID.fromString("00000000-0000-0000-0000-000000000901"),
            mediaType = MediaType.VIDEO,
            mediaUrl = "https://cdn.example.com/approved.mp4",
            thumbnailUrl = "https://cdn.example.com/thumb.jpg",
            externalLink = "https://example.com",
        )
}
