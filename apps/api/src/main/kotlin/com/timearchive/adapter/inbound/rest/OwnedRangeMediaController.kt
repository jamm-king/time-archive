package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CompleteOwnedRangeMediaUpload
import com.timearchive.application.CreateOwnedRangeMediaAsset
import com.timearchive.application.CreateOwnedRangeMediaUploadRequest
import com.timearchive.application.ListOwnedRangeMediaAssets
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/owned-ranges/{ownershipRecordId}/media")
class OwnedRangeMediaController(
    private val completeOwnedRangeMediaUpload: CompleteOwnedRangeMediaUpload,
    private val createOwnedRangeMediaAsset: CreateOwnedRangeMediaAsset,
    private val createOwnedRangeMediaUploadRequest: CreateOwnedRangeMediaUploadRequest,
    private val currentUserSession: CurrentUserSession,
    private val listOwnedRangeMediaAssets: ListOwnedRangeMediaAssets,
) {
    @PostMapping("/upload-requests")
    @ResponseStatus(HttpStatus.CREATED)
    fun createUploadRequest(
        httpRequest: HttpServletRequest,
        @PathVariable ownershipRecordId: UUID,
        @Valid @RequestBody request: CreateMediaUploadRequest,
    ): MediaUploadRequestResponse {
        val currentUserId = currentUserSession.requireCurrentUserId(httpRequest)
        val result = createOwnedRangeMediaUploadRequest.create(
            CreateOwnedRangeMediaUploadRequest.Command(
                currentUserId = currentUserId,
                ownershipRecordId = ownershipRecordId,
                mediaType = requireNotNull(request.mediaType),
                originalFilename = requireNotNull(request.originalFilename),
                contentType = requireNotNull(request.contentType),
                contentLengthBytes = requireNotNull(request.contentLengthBytes),
            ),
        )

        return MediaUploadRequestResponse.from(result)
    }

    @PostMapping("/upload-requests/{uploadRequestId}/complete")
    fun completeUploadRequest(
        httpRequest: HttpServletRequest,
        @PathVariable ownershipRecordId: UUID,
        @PathVariable uploadRequestId: UUID,
    ): CompleteMediaUploadResponse {
        val currentUserId = currentUserSession.requireCurrentUserId(httpRequest)
        val result = completeOwnedRangeMediaUpload.complete(
            CompleteOwnedRangeMediaUpload.Command(
                currentUserId = currentUserId,
                ownershipRecordId = ownershipRecordId,
                uploadRequestId = uploadRequestId,
            ),
        )

        return CompleteMediaUploadResponse.from(result)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        httpRequest: HttpServletRequest,
        @PathVariable ownershipRecordId: UUID,
        @Valid @RequestBody request: CreateOwnedRangeMediaRequest,
    ): MediaAssetResponse {
        val currentUserId = currentUserSession.requireCurrentUserId(httpRequest)
        val asset = createOwnedRangeMediaAsset.create(
            CreateOwnedRangeMediaAsset.Command(
                currentUserId = currentUserId,
                ownershipRecordId = ownershipRecordId,
                mediaType = requireNotNull(request.mediaType),
                originalFileUrl = requireNotNull(request.originalFileUrl),
                thumbnailUrl = request.thumbnailUrl,
                externalLink = request.externalLink,
            ),
        )

        return MediaAssetResponse.from(asset)
    }

    @GetMapping
    fun list(
        httpRequest: HttpServletRequest,
        @PathVariable ownershipRecordId: UUID,
    ): List<MediaAssetResponse> {
        val currentUserId = currentUserSession.requireCurrentUserId(httpRequest)
        return listOwnedRangeMediaAssets.list(
            ListOwnedRangeMediaAssets.Query(
                currentUserId = currentUserId,
                ownershipRecordId = ownershipRecordId,
            ),
        ).map(MediaAssetResponse::from)
    }
}
