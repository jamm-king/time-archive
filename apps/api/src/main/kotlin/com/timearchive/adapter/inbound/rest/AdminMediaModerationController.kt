package com.timearchive.adapter.inbound.rest

import com.timearchive.application.ApproveMediaAsset
import com.timearchive.application.CreateAdminMediaPreviewUrl
import com.timearchive.application.GetCurrentUser
import com.timearchive.application.HideMediaAsset
import com.timearchive.application.ListMediaModerationQueue
import com.timearchive.application.RejectMediaAsset
import com.timearchive.domain.model.ModerationStatus
import com.timearchive.domain.model.UserRole
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/media/assets")
class AdminMediaModerationController(
    private val listMediaModerationQueue: ListMediaModerationQueue,
    private val createAdminMediaPreviewUrl: CreateAdminMediaPreviewUrl,
    private val approveMediaAsset: ApproveMediaAsset,
    private val rejectMediaAsset: RejectMediaAsset,
    private val hideMediaAsset: HideMediaAsset,
    private val currentUserSession: CurrentUserSession,
    private val getCurrentUser: GetCurrentUser,
) {
    @GetMapping
    fun list(
        httpRequest: HttpServletRequest,
        @RequestParam(defaultValue = "UPLOADED") status: ModerationStatus,
    ): List<MediaAssetResponse> {
        requireAdminId(httpRequest)
        return listMediaModerationQueue.list(
            ListMediaModerationQueue.Query(status = status),
        ).map(MediaAssetResponse::from)
    }

    @GetMapping("/{mediaAssetId}/preview-url")
    fun previewUrl(
        httpRequest: HttpServletRequest,
        @PathVariable mediaAssetId: UUID,
    ): AdminMediaPreviewUrlResponse {
        val adminId = requireAdminId(httpRequest)
        val result = createAdminMediaPreviewUrl.create(
            CreateAdminMediaPreviewUrl.Command(
                adminId = adminId,
                mediaAssetId = mediaAssetId,
            ),
        )

        return AdminMediaPreviewUrlResponse(
            mediaAssetId = result.mediaAssetId,
            previewUrl = result.previewUrl,
            expiresAt = result.expiresAt,
        )
    }

    @PostMapping("/{mediaAssetId}/approve")
    fun approve(
        httpRequest: HttpServletRequest,
        @PathVariable mediaAssetId: UUID,
        @Valid @RequestBody request: ApproveMediaAssetRequest,
    ): MediaAssetResponse {
        val adminId = requireAdminId(httpRequest)
        return MediaAssetResponse.from(
            approveMediaAsset.approve(
                ApproveMediaAsset.Command(
                    adminId = adminId,
                    mediaAssetId = mediaAssetId,
                    approvedFileUrl = requireNotNull(request.approvedFileUrl),
                    thumbnailUrl = request.thumbnailUrl,
                ),
            ),
        )
    }

    @PostMapping("/{mediaAssetId}/reject")
    fun reject(
        httpRequest: HttpServletRequest,
        @PathVariable mediaAssetId: UUID,
    ): MediaAssetResponse {
        val adminId = requireAdminId(httpRequest)
        return MediaAssetResponse.from(
            rejectMediaAsset.reject(
                RejectMediaAsset.Command(
                    adminId = adminId,
                    mediaAssetId = mediaAssetId,
                ),
            ),
        )
    }

    @PostMapping("/{mediaAssetId}/hide")
    fun hide(
        httpRequest: HttpServletRequest,
        @PathVariable mediaAssetId: UUID,
    ): MediaAssetResponse {
        val adminId = requireAdminId(httpRequest)
        return MediaAssetResponse.from(
            hideMediaAsset.hide(
                HideMediaAsset.Command(
                    adminId = adminId,
                    mediaAssetId = mediaAssetId,
                ),
            ),
        )
    }

    private fun requireAdminId(request: HttpServletRequest): UUID {
        val userId = currentUserSession.requireCurrentUserId(request)
        val user = getCurrentUser.get(GetCurrentUser.Query(userId = userId))
        require(user.role == UserRole.ADMIN) { "admin permission required" }
        return user.id
    }
}
