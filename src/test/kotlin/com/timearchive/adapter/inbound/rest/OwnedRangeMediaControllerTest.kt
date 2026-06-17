package com.timearchive.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.timearchive.application.CreateOwnedRangeMediaAsset
import com.timearchive.application.ListOwnedRangeMediaAssets
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

class OwnedRangeMediaControllerTest {
    private val createOwnedRangeMediaAsset: CreateOwnedRangeMediaAsset = mockk()
    private val listOwnedRangeMediaAssets: ListOwnedRangeMediaAssets = mockk()
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
    private lateinit var mockMvc: MockMvc

    private val currentUserId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000701")
    private val ownershipRecordId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000702")

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                OwnedRangeMediaController(
                    createOwnedRangeMediaAsset = createOwnedRangeMediaAsset,
                    listOwnedRangeMediaAssets = listOwnedRangeMediaAssets,
                ),
            )
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `creates media asset for owned range`() {
        every { createOwnedRangeMediaAsset.create(any()) } returns mediaAsset()

        mockMvc.post("/api/owned-ranges/$ownershipRecordId/media") {
            header("X-User-Id", currentUserId.toString())
            contentType = APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "mediaType" to "IMAGE",
                    "originalFileUrl" to "https://cdn.example.com/original.png",
                    "thumbnailUrl" to "https://cdn.example.com/thumb.png",
                    "externalLink" to "https://example.com",
                ),
            )
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.mediaAssetId") { value("00000000-0000-0000-0000-000000000703") }
                jsonPath("$.ownershipRecordId") { value(ownershipRecordId.toString()) }
                jsonPath("$.ownerId") { value(currentUserId.toString()) }
                jsonPath("$.mediaType") { value("IMAGE") }
                jsonPath("$.moderationStatus") { value("UPLOADED") }
                jsonPath("$.publiclyVisible") { value(false) }
            }

        verify {
            createOwnedRangeMediaAsset.create(
                CreateOwnedRangeMediaAsset.Command(
                    currentUserId = currentUserId,
                    ownershipRecordId = ownershipRecordId,
                    mediaType = MediaType.IMAGE,
                    originalFileUrl = "https://cdn.example.com/original.png",
                    thumbnailUrl = "https://cdn.example.com/thumb.png",
                    externalLink = "https://example.com",
                ),
            )
        }
    }

    @Test
    fun `lists media assets for owned range`() {
        every { listOwnedRangeMediaAssets.list(any()) } returns listOf(mediaAsset())

        mockMvc.get("/api/owned-ranges/$ownershipRecordId/media") {
            header("X-User-Id", currentUserId.toString())
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].mediaAssetId") { value("00000000-0000-0000-0000-000000000703") }
            }

        verify {
            listOwnedRangeMediaAssets.list(
                ListOwnedRangeMediaAssets.Query(
                    currentUserId = currentUserId,
                    ownershipRecordId = ownershipRecordId,
                ),
            )
        }
    }

    @Test
    fun `rejects missing current user header`() {
        mockMvc.post("/api/owned-ranges/$ownershipRecordId/media") {
            contentType = APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "mediaType" to "IMAGE",
                    "originalFileUrl" to "https://cdn.example.com/original.png",
                ),
            )
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
    }

    @Test
    fun `maps ownership access denial to forbidden`() {
        every { createOwnedRangeMediaAsset.create(any()) } throws
            IllegalArgumentException("ownership record is not owned by current user")

        mockMvc.post("/api/owned-ranges/$ownershipRecordId/media") {
            header("X-User-Id", currentUserId.toString())
            contentType = APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "mediaType" to "IMAGE",
                    "originalFileUrl" to "https://cdn.example.com/original.png",
                ),
            )
        }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("OWNERSHIP_ACCESS_DENIED") }
            }
    }

    private fun mediaAsset(): MediaAsset =
        MediaAsset.uploaded(
            id = UUID.fromString("00000000-0000-0000-0000-000000000703"),
            ownershipRecordId = ownershipRecordId,
            ownerId = currentUserId,
            mediaType = MediaType.IMAGE,
            originalFileUrl = "https://cdn.example.com/original.png",
            thumbnailUrl = "https://cdn.example.com/thumb.png",
            externalLink = "https://example.com",
            now = Instant.parse("2026-06-17T00:00:00Z"),
        )
}
