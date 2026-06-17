package com.timearchive.adapter.inbound.rest

import com.timearchive.application.ApproveMediaAsset
import com.timearchive.application.HideMediaAsset
import com.timearchive.application.ListMediaModerationQueue
import com.timearchive.application.RejectMediaAsset
import com.timearchive.domain.model.ModerationStatus
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/media/assets")
class AdminMediaModerationController(
    private val listMediaModerationQueue: ListMediaModerationQueue,
    private val approveMediaAsset: ApproveMediaAsset,
    private val rejectMediaAsset: RejectMediaAsset,
    private val hideMediaAsset: HideMediaAsset,
) {
    @GetMapping
    fun list(
        @RequestHeader("X-Admin-Id") adminId: UUID,
        @RequestParam(defaultValue = "UPLOADED") status: ModerationStatus,
    ): List<MediaAssetResponse> =
        listMediaModerationQueue.list(
            ListMediaModerationQueue.Query(status = status),
        ).map(MediaAssetResponse::from)

    @PostMapping("/{mediaAssetId}/approve")
    fun approve(
        @RequestHeader("X-Admin-Id") adminId: UUID,
        @PathVariable mediaAssetId: UUID,
        @Valid @RequestBody request: ApproveMediaAssetRequest,
    ): MediaAssetResponse =
        MediaAssetResponse.from(
            approveMediaAsset.approve(
                ApproveMediaAsset.Command(
                    adminId = adminId,
                    mediaAssetId = mediaAssetId,
                    approvedFileUrl = requireNotNull(request.approvedFileUrl),
                    thumbnailUrl = request.thumbnailUrl,
                ),
            ),
        )

    @PostMapping("/{mediaAssetId}/reject")
    fun reject(
        @RequestHeader("X-Admin-Id") adminId: UUID,
        @PathVariable mediaAssetId: UUID,
    ): MediaAssetResponse =
        MediaAssetResponse.from(
            rejectMediaAsset.reject(
                RejectMediaAsset.Command(
                    adminId = adminId,
                    mediaAssetId = mediaAssetId,
                ),
            ),
        )

    @PostMapping("/{mediaAssetId}/hide")
    fun hide(
        @RequestHeader("X-Admin-Id") adminId: UUID,
        @PathVariable mediaAssetId: UUID,
    ): MediaAssetResponse =
        MediaAssetResponse.from(
            hideMediaAsset.hide(
                HideMediaAsset.Command(
                    adminId = adminId,
                    mediaAssetId = mediaAssetId,
                ),
            ),
        )
}
