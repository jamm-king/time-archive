package com.timearchive.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.timearchive.application.ApproveMediaAsset
import com.timearchive.application.HideMediaAsset
import com.timearchive.application.ListMediaModerationQueue
import com.timearchive.application.RejectMediaAsset
import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class AdminMediaModerationControllerTest {
    private val listMediaModerationQueue: ListMediaModerationQueue = mockk()
    private val approveMediaAsset: ApproveMediaAsset = mockk()
    private val rejectMediaAsset: RejectMediaAsset = mockk()
    private val hideMediaAsset: HideMediaAsset = mockk()
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
    private lateinit var mockMvc: MockMvc

    private val adminId: UUID = UUID.fromString("00000000-0000-0000-0000-000000004001")
    private val mediaAssetId: UUID = UUID.fromString("00000000-0000-0000-0000-000000004002")

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                AdminMediaModerationController(
                    listMediaModerationQueue = listMediaModerationQueue,
                    approveMediaAsset = approveMediaAsset,
                    rejectMediaAsset = rejectMediaAsset,
                    hideMediaAsset = hideMediaAsset,
                ),
            )
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `lists media assets by moderation status`() {
        every { listMediaModerationQueue.list(any()) } returns listOf(uploadedMediaAsset())

        mockMvc.get("/api/admin/media/assets") {
            header("X-Admin-Id", adminId.toString())
            param("status", "UPLOADED")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].mediaAssetId") { value(mediaAssetId.toString()) }
                jsonPath("$[0].moderationStatus") { value("UPLOADED") }
            }

        verify {
            listMediaModerationQueue.list(
                ListMediaModerationQueue.Query(status = com.timearchive.domain.model.ModerationStatus.UPLOADED),
            )
        }
    }

    @Test
    fun `approves media asset`() {
        every { approveMediaAsset.approve(any()) } returns approvedMediaAsset()

        mockMvc.post("/api/admin/media/assets/$mediaAssetId/approve") {
            header("X-Admin-Id", adminId.toString())
            contentType = APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "approvedFileUrl" to "https://cdn.example.test/approved.png",
                    "thumbnailUrl" to "https://cdn.example.test/thumb.png",
                ),
            )
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.mediaAssetId") { value(mediaAssetId.toString()) }
                jsonPath("$.moderationStatus") { value("APPROVED") }
                jsonPath("$.approvedFileUrl") { value("https://cdn.example.test/approved.png") }
            }

        verify {
            approveMediaAsset.approve(
                ApproveMediaAsset.Command(
                    adminId = adminId,
                    mediaAssetId = mediaAssetId,
                    approvedFileUrl = "https://cdn.example.test/approved.png",
                    thumbnailUrl = "https://cdn.example.test/thumb.png",
                ),
            )
        }
    }

    @Test
    fun `rejects media asset`() {
        every { rejectMediaAsset.reject(any()) } returns uploadedMediaAsset()
            .reject(now = Instant.parse("2026-06-17T00:01:00Z"))

        mockMvc.post("/api/admin/media/assets/$mediaAssetId/reject") {
            header("X-Admin-Id", adminId.toString())
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.moderationStatus") { value("REJECTED") }
            }
    }

    @Test
    fun `hides media asset`() {
        every { hideMediaAsset.hide(any()) } returns approvedMediaAsset()
            .hide(now = Instant.parse("2026-06-17T00:02:00Z"))

        mockMvc.post("/api/admin/media/assets/$mediaAssetId/hide") {
            header("X-Admin-Id", adminId.toString())
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.moderationStatus") { value("HIDDEN") }
                jsonPath("$.publiclyVisible") { value(false) }
            }
    }

    @Test
    fun `maps invalid moderation transition to conflict`() {
        every { hideMediaAsset.hide(any()) } throws IllegalArgumentException("media asset is not hideable")

        mockMvc.post("/api/admin/media/assets/$mediaAssetId/hide") {
            header("X-Admin-Id", adminId.toString())
        }
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("MEDIA_ASSET_NOT_HIDEABLE") }
            }
    }

    private fun uploadedMediaAsset(): MediaAsset =
        MediaAsset.uploaded(
            id = mediaAssetId,
            ownershipRecordId = UUID.fromString("00000000-0000-0000-0000-000000004003"),
            ownerId = UUID.fromString("00000000-0000-0000-0000-000000004004"),
            mediaType = MediaType.IMAGE,
            originalFileUrl = "https://cdn.example.test/original.png",
            now = Instant.parse("2026-06-17T00:00:00Z"),
        )

    private fun approvedMediaAsset(): MediaAsset =
        uploadedMediaAsset().approve(
            approvedFileUrl = "https://cdn.example.test/approved.png",
            thumbnailUrl = "https://cdn.example.test/thumb.png",
            now = Instant.parse("2026-06-17T00:01:00Z"),
        )
}
