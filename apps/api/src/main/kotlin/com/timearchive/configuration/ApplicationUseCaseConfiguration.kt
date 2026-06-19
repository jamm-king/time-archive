package com.timearchive.configuration

import com.timearchive.application.ApproveMediaAsset
import com.timearchive.application.AuthenticateUser
import com.timearchive.application.CompletePrimaryPurchase
import com.timearchive.application.CompleteOwnedRangeMediaUpload
import com.timearchive.application.CreateCheckout
import com.timearchive.application.CreateAdminMediaPreviewUrl
import com.timearchive.application.CreateOwnedRangeMediaAsset
import com.timearchive.application.CreateOwnedRangeMediaUploadRequest
import com.timearchive.application.CheckAvailability
import com.timearchive.application.GetCurrentUser
import com.timearchive.application.HideMediaAsset
import com.timearchive.application.ListCurrentUserOwnedRanges
import com.timearchive.application.ListMediaModerationQueue
import com.timearchive.application.ListOwnedRangeMediaAssets
import com.timearchive.application.ListPublicTimelineSegments
import com.timearchive.application.RejectMediaAsset
import com.timearchive.application.RegisterUser
import com.timearchive.application.ReserveTimeRange
import com.timearchive.domain.port.AuditLogPort
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.MediaObjectStoragePort
import com.timearchive.domain.port.MediaUploadRequestRepository
import com.timearchive.domain.port.OutboxPort
import com.timearchive.domain.port.OwnershipRepository
import com.timearchive.domain.port.PasswordHasherPort
import com.timearchive.domain.port.PaymentEventRepository
import com.timearchive.domain.port.PaymentPort
import com.timearchive.domain.port.PublicTimelineSegmentRepository
import com.timearchive.domain.port.PurchaseRepository
import com.timearchive.domain.port.PurchaseReservationRepository
import com.timearchive.domain.port.TransactionPort
import com.timearchive.domain.port.UserAccountRepository
import com.timearchive.domain.model.UserAccount
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Value
import java.time.Duration
import java.time.Instant

@Configuration
class ApplicationUseCaseConfiguration {
    @Bean
    fun clockPort(): ClockPort = ClockPort { Instant.now() }

    @Bean
    fun registerUser(
        userAccountRepository: UserAccountRepository,
        passwordHasherPort: PasswordHasherPort,
        clockPort: ClockPort,
        @Value("\${time-archive.security.initial-admin-emails:}") initialAdminEmails: String,
    ): RegisterUser =
        RegisterUser(
            userAccountRepository = userAccountRepository,
            passwordHasherPort = passwordHasherPort,
            clockPort = clockPort,
            initialAdminEmails = initialAdminEmails
                .split(",")
                .map(String::trim)
                .filter(String::isNotBlank)
                .map(UserAccount::normalizeEmail)
                .toSet(),
        )

    @Bean
    fun authenticateUser(
        userAccountRepository: UserAccountRepository,
        passwordHasherPort: PasswordHasherPort,
    ): AuthenticateUser =
        AuthenticateUser(
            userAccountRepository = userAccountRepository,
            passwordHasherPort = passwordHasherPort,
        )

    @Bean
    fun getCurrentUser(
        userAccountRepository: UserAccountRepository,
    ): GetCurrentUser = GetCurrentUser(userAccountRepository = userAccountRepository)

    @Bean
    fun listCurrentUserOwnedRanges(
        ownershipRepository: OwnershipRepository,
    ): ListCurrentUserOwnedRanges =
        ListCurrentUserOwnedRanges(ownershipRepository = ownershipRepository)

    @Bean
    fun reserveTimeRange(
        ownershipRepository: OwnershipRepository,
        purchaseReservationRepository: PurchaseReservationRepository,
        clockPort: ClockPort,
    ): ReserveTimeRange =
        ReserveTimeRange(
            ownershipRepository = ownershipRepository,
            purchaseReservationRepository = purchaseReservationRepository,
            clockPort = clockPort,
        )

    @Bean
    fun checkAvailability(
        ownershipRepository: OwnershipRepository,
        purchaseReservationRepository: PurchaseReservationRepository,
        clockPort: ClockPort,
    ): CheckAvailability =
        CheckAvailability(
            ownershipRepository = ownershipRepository,
            purchaseReservationRepository = purchaseReservationRepository,
            clockPort = clockPort,
        )

    @Bean
    fun createCheckout(
        transactionPort: TransactionPort,
        purchaseReservationRepository: PurchaseReservationRepository,
        paymentPort: PaymentPort,
        clockPort: ClockPort,
    ): CreateCheckout =
        CreateCheckout(
            transactionPort = transactionPort,
            purchaseReservationRepository = purchaseReservationRepository,
            paymentPort = paymentPort,
            clockPort = clockPort,
        )

    @Bean
    fun createOwnedRangeMediaAsset(
        transactionPort: TransactionPort,
        ownershipRepository: OwnershipRepository,
        mediaAssetRepository: MediaAssetRepository,
        clockPort: ClockPort,
    ): CreateOwnedRangeMediaAsset =
        CreateOwnedRangeMediaAsset(
            transactionPort = transactionPort,
            ownershipRepository = ownershipRepository,
            mediaAssetRepository = mediaAssetRepository,
            clockPort = clockPort,
        )

    @Bean
    fun createOwnedRangeMediaUploadRequest(
        transactionPort: TransactionPort,
        ownershipRepository: OwnershipRepository,
        mediaUploadRequestRepository: MediaUploadRequestRepository,
        mediaObjectStoragePort: MediaObjectStoragePort,
        clockPort: ClockPort,
        @Value("\${time-archive.storage.s3.upload-url-expiration-seconds}") uploadUrlExpirationSeconds: Long,
    ): CreateOwnedRangeMediaUploadRequest =
        CreateOwnedRangeMediaUploadRequest(
            transactionPort = transactionPort,
            ownershipRepository = ownershipRepository,
            mediaUploadRequestRepository = mediaUploadRequestRepository,
            mediaObjectStoragePort = mediaObjectStoragePort,
            clockPort = clockPort,
            uploadUrlTtl = Duration.ofSeconds(uploadUrlExpirationSeconds),
        )

    @Bean
    fun completeOwnedRangeMediaUpload(
        transactionPort: TransactionPort,
        ownershipRepository: OwnershipRepository,
        mediaUploadRequestRepository: MediaUploadRequestRepository,
        mediaAssetRepository: MediaAssetRepository,
        mediaObjectStoragePort: MediaObjectStoragePort,
        clockPort: ClockPort,
    ): CompleteOwnedRangeMediaUpload =
        CompleteOwnedRangeMediaUpload(
            transactionPort = transactionPort,
            ownershipRepository = ownershipRepository,
            mediaUploadRequestRepository = mediaUploadRequestRepository,
            mediaAssetRepository = mediaAssetRepository,
            mediaObjectStoragePort = mediaObjectStoragePort,
            clockPort = clockPort,
        )

    @Bean
    fun listOwnedRangeMediaAssets(
        ownershipRepository: OwnershipRepository,
        mediaAssetRepository: MediaAssetRepository,
    ): ListOwnedRangeMediaAssets =
        ListOwnedRangeMediaAssets(
            ownershipRepository = ownershipRepository,
            mediaAssetRepository = mediaAssetRepository,
        )

    @Bean
    fun listMediaModerationQueue(
        mediaAssetRepository: MediaAssetRepository,
    ): ListMediaModerationQueue =
        ListMediaModerationQueue(mediaAssetRepository = mediaAssetRepository)

    @Bean
    fun createAdminMediaPreviewUrl(
        mediaAssetRepository: MediaAssetRepository,
        mediaObjectStoragePort: MediaObjectStoragePort,
        clockPort: ClockPort,
        @Value("\${time-archive.storage.s3.preview-url-expiration-seconds}") previewUrlExpirationSeconds: Long,
    ): CreateAdminMediaPreviewUrl =
        CreateAdminMediaPreviewUrl(
            mediaAssetRepository = mediaAssetRepository,
            mediaObjectStoragePort = mediaObjectStoragePort,
            clockPort = clockPort,
            previewUrlTtl = Duration.ofSeconds(previewUrlExpirationSeconds),
        )

    @Bean
    fun listPublicTimelineSegments(
        publicTimelineSegmentRepository: PublicTimelineSegmentRepository,
        mediaObjectStoragePort: MediaObjectStoragePort,
        clockPort: ClockPort,
        @Value("\${time-archive.storage.s3.playback-url-expiration-seconds}") playbackUrlExpirationSeconds: Long,
    ): ListPublicTimelineSegments =
        ListPublicTimelineSegments(
            publicTimelineSegmentRepository = publicTimelineSegmentRepository,
            mediaObjectStoragePort = mediaObjectStoragePort,
            clockPort = clockPort,
            playbackUrlTtl = Duration.ofSeconds(playbackUrlExpirationSeconds),
        )

    @Bean
    fun approveMediaAsset(
        transactionPort: TransactionPort,
        mediaAssetRepository: MediaAssetRepository,
        auditLogPort: AuditLogPort,
        clockPort: ClockPort,
    ): ApproveMediaAsset =
        ApproveMediaAsset(
            transactionPort = transactionPort,
            mediaAssetRepository = mediaAssetRepository,
            auditLogPort = auditLogPort,
            clockPort = clockPort,
        )

    @Bean
    fun rejectMediaAsset(
        transactionPort: TransactionPort,
        mediaAssetRepository: MediaAssetRepository,
        auditLogPort: AuditLogPort,
        clockPort: ClockPort,
    ): RejectMediaAsset =
        RejectMediaAsset(
            transactionPort = transactionPort,
            mediaAssetRepository = mediaAssetRepository,
            auditLogPort = auditLogPort,
            clockPort = clockPort,
        )

    @Bean
    fun hideMediaAsset(
        transactionPort: TransactionPort,
        mediaAssetRepository: MediaAssetRepository,
        auditLogPort: AuditLogPort,
        clockPort: ClockPort,
    ): HideMediaAsset =
        HideMediaAsset(
            transactionPort = transactionPort,
            mediaAssetRepository = mediaAssetRepository,
            auditLogPort = auditLogPort,
            clockPort = clockPort,
        )

    @Bean
    fun completePrimaryPurchase(
        transactionPort: TransactionPort,
        purchaseReservationRepository: PurchaseReservationRepository,
        purchaseRepository: PurchaseRepository,
        paymentEventRepository: PaymentEventRepository,
        ownershipRepository: OwnershipRepository,
        auditLogPort: AuditLogPort,
        outboxPort: OutboxPort,
        clockPort: ClockPort,
    ): CompletePrimaryPurchase =
        CompletePrimaryPurchase(
            transactionPort = transactionPort,
            purchaseReservationRepository = purchaseReservationRepository,
            purchaseRepository = purchaseRepository,
            paymentEventRepository = paymentEventRepository,
            ownershipRepository = ownershipRepository,
            auditLogPort = auditLogPort,
            outboxPort = outboxPort,
            clockPort = clockPort,
        )
}
