package com.timearchive.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.timearchive.application.CompleteOwnedRangeMediaUpload
import com.timearchive.application.CreateOwnedRangeMediaAsset
import com.timearchive.application.CreateOwnedRangeMediaUploadRequest
import com.timearchive.application.ListOwnedRangeMediaAssets
import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.MediaUploadRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class OwnedRangeMediaControllerTest {
    private val completeOwnedRangeMediaUpload: CompleteOwnedRangeMediaUpload = mockk()
    private val createOwnedRangeMediaAsset: CreateOwnedRangeMediaAsset = mockk()
    private val createOwnedRangeMediaUploadRequest: CreateOwnedRangeMediaUploadRequest = mockk()
    private val currentUserSession = CurrentUserSession()
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
                    completeOwnedRangeMediaUpload = completeOwnedRangeMediaUpload,
                    createOwnedRangeMediaAsset = createOwnedRangeMediaAsset,
                    createOwnedRangeMediaUploadRequest = createOwnedRangeMediaUploadRequest,
                    currentUserSession = currentUserSession,
                    listOwnedRangeMediaAssets = listOwnedRangeMediaAssets,
                ),
            )
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `completes media upload request`() {
        every { completeOwnedRangeMediaUpload.complete(any()) } returns completeUploadResult()

        mockMvc.post("/api/owned-ranges/$ownershipRecordId/media/upload-requests/00000000-0000-0000-0000-000000000704/complete") {
            this.session = signedInSession(currentUserId)
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.uploadRequestId") { value("00000000-0000-0000-0000-000000000704") }
                jsonPath("$.alreadyCompleted") { value(false) }
                jsonPath("$.mediaAsset.mediaAssetId") { value("00000000-0000-0000-0000-000000000703") }
                jsonPath("$.mediaAsset.moderationStatus") { value("UPLOADED") }
            }

        verify {
            completeOwnedRangeMediaUpload.complete(
                CompleteOwnedRangeMediaUpload.Command(
                    currentUserId = currentUserId,
                    ownershipRecordId = ownershipRecordId,
                    uploadRequestId = UUID.fromString("00000000-0000-0000-0000-000000000704"),
                ),
            )
        }
    }

    @Test
    fun `creates media upload request for owned range`() {
        every { createOwnedRangeMediaUploadRequest.create(any()) } returns uploadRequestResult()

        mockMvc.post("/api/owned-ranges/$ownershipRecordId/media/upload-requests") {
            this.session = signedInSession(currentUserId)
            contentType = APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "mediaType" to "IMAGE",
                    "originalFilename" to "original.png",
                    "contentType" to "image/png",
                    "contentLengthBytes" to 1024,
                ),
            )
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.uploadRequestId") { value("00000000-0000-0000-0000-000000000704") }
                jsonPath("$.ownershipRecordId") { value(ownershipRecordId.toString()) }
                jsonPath("$.ownerId") { value(currentUserId.toString()) }
                jsonPath("$.mediaType") { value("IMAGE") }
                jsonPath("$.originalFilename") { value("original.png") }
                jsonPath("$.contentType") { value("image/png") }
                jsonPath("$.contentLengthBytes") { value(1024) }
                jsonPath("$.uploadUrl") { value("http://localhost:9000/time-archive-media/media/originals/test") }
                jsonPath("$.requiredHeaders['content-type']") { value("image/png") }
                jsonPath("$.status") { value("REQUESTED") }
            }

        verify {
            createOwnedRangeMediaUploadRequest.create(
                CreateOwnedRangeMediaUploadRequest.Command(
                    currentUserId = currentUserId,
                    ownershipRecordId = ownershipRecordId,
                    mediaType = MediaType.IMAGE,
                    originalFilename = "original.png",
                    contentType = "image/png",
                    contentLengthBytes = 1024,
                ),
            )
        }
    }

    @Test
    fun `creates media asset for owned range`() {
        every { createOwnedRangeMediaAsset.create(any()) } returns mediaAsset()

        mockMvc.post("/api/owned-ranges/$ownershipRecordId/media") {
            this.session = signedInSession(currentUserId)
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
            this.session = signedInSession(currentUserId)
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
    fun `rejects request without session`() {
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
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            }
    }

    @Test
    fun `maps ownership access denial to forbidden`() {
        every { createOwnedRangeMediaAsset.create(any()) } throws
            IllegalArgumentException("ownership record is not owned by current user")

        mockMvc.post("/api/owned-ranges/$ownershipRecordId/media") {
            this.session = signedInSession(currentUserId)
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

    private fun signedInSession(userId: UUID): MockHttpSession =
        MockHttpSession().also { currentUserSession.signIn(it, userId) }

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

    private fun uploadRequestResult(): CreateOwnedRangeMediaUploadRequest.Result =
        CreateOwnedRangeMediaUploadRequest.Result(
            uploadRequest = MediaUploadRequest.requested(
                id = UUID.fromString("00000000-0000-0000-0000-000000000704"),
                ownershipRecordId = ownershipRecordId,
                ownerId = currentUserId,
                mediaType = MediaType.IMAGE,
                originalFilename = "original.png",
                contentType = "image/png",
                contentLengthBytes = 1024,
                objectKey = "media/originals/test",
                originalFileUrl = "http://localhost:9000/time-archive-media/media/originals/test",
                now = Instant.parse("2026-06-17T00:00:00Z"),
                expiresAt = Instant.parse("2026-06-17T00:10:00Z"),
            ),
            uploadUrl = "http://localhost:9000/time-archive-media/media/originals/test",
            requiredHeaders = mapOf("content-type" to "image/png"),
        )

    private fun completeUploadResult(): CompleteOwnedRangeMediaUpload.Result =
        CompleteOwnedRangeMediaUpload.Result(
            uploadRequest = uploadRequestResult().uploadRequest.complete(
                mediaAssetId = UUID.fromString("00000000-0000-0000-0000-000000000703"),
                now = Instant.parse("2026-06-17T00:01:00Z"),
            ),
            mediaAsset = mediaAsset(),
            alreadyCompleted = false,
        )
}
