package com.timearchive.application

import com.timearchive.domain.model.MediaAsset
import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.ModerationStatus
import com.timearchive.domain.model.AuditLog
import com.timearchive.domain.port.AuditLogPort
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaAssetRepository
import com.timearchive.domain.port.TransactionPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AdminMediaModerationTest {
    private val now: Instant = Instant.parse("2026-06-17T00:00:00Z")
    private val adminId: UUID = UUID.fromString("00000000-0000-0000-0000-000000003001")
    private val mediaAssetId: UUID = UUID.fromString("00000000-0000-0000-0000-000000003002")

    @Test
    fun `lists media assets by moderation status`() {
        val uploaded = uploadedMediaAsset()
        val repository = FakeMediaAssetRepository(assets = mutableListOf(uploaded))
        val useCase = ListMediaModerationQueue(mediaAssetRepository = repository)

        val result = useCase.list(ListMediaModerationQueue.Query(status = ModerationStatus.UPLOADED))

        assertThat(result).containsExactly(uploaded)
    }

    @Test
    fun `approves uploaded media asset`() {
        val repository = FakeMediaAssetRepository(assets = mutableListOf(uploadedMediaAsset()))
        val auditLogPort = FakeAuditLogPort()
        val useCase = ApproveMediaAsset(
            transactionPort = ImmediateTransactionPort,
            mediaAssetRepository = repository,
            auditLogPort = auditLogPort,
            clockPort = ClockPort { now.plusSeconds(10) },
            idGenerator = { UUID.fromString("00000000-0000-0000-0000-000000003101") },
        )

        val result = useCase.approve(
            ApproveMediaAsset.Command(
                adminId = adminId,
                mediaAssetId = mediaAssetId,
                approvedFileUrl = "https://cdn.example.test/approved.png",
                thumbnailUrl = "https://cdn.example.test/thumb.png",
            ),
        )

        assertThat(result.moderationStatus).isEqualTo(ModerationStatus.APPROVED)
        assertThat(result.approvedFileUrl).isEqualTo("https://cdn.example.test/approved.png")
        assertThat(result.thumbnailUrl).isEqualTo("https://cdn.example.test/thumb.png")
        assertThat(result.isPubliclyVisible).isTrue()
        assertThat(repository.updated).containsExactly(result)
        val auditLog = auditLogPort.appended.single()
        assertThat(auditLog.id).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000003101"))
        assertThat(auditLog.actorUserId).isEqualTo(adminId)
        assertThat(auditLog.actorType).isEqualTo("USER")
        assertThat(auditLog.action).isEqualTo("MEDIA_ASSET_APPROVED")
        assertThat(auditLog.resourceType).isEqualTo("MEDIA_ASSET")
        assertThat(auditLog.resourceId).isEqualTo(mediaAssetId)
        assertThat(auditLog.beforeState).isEqualTo(
            """{"moderationStatus":"UPLOADED","approvedFileUrl":null,"thumbnailUrl":null}""",
        )
        assertThat(auditLog.afterState).isEqualTo(
            """{"moderationStatus":"APPROVED","approvedFileUrl":"https://cdn.example.test/approved.png","thumbnailUrl":"https://cdn.example.test/thumb.png"}""",
        )
        assertThat(auditLog.createdAt).isEqualTo(now.plusSeconds(10))
    }

    @Test
    fun `rejects uploaded media asset`() {
        val repository = FakeMediaAssetRepository(assets = mutableListOf(uploadedMediaAsset()))
        val auditLogPort = FakeAuditLogPort()
        val useCase = RejectMediaAsset(
            transactionPort = ImmediateTransactionPort,
            mediaAssetRepository = repository,
            auditLogPort = auditLogPort,
            clockPort = ClockPort { now.plusSeconds(10) },
            idGenerator = { UUID.fromString("00000000-0000-0000-0000-000000003102") },
        )

        val result = useCase.reject(
            RejectMediaAsset.Command(
                adminId = adminId,
                mediaAssetId = mediaAssetId,
            ),
        )

        assertThat(result.moderationStatus).isEqualTo(ModerationStatus.REJECTED)
        assertThat(result.isPubliclyVisible).isFalse()
        val auditLog = auditLogPort.appended.single()
        assertThat(auditLog.id).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000003102"))
        assertThat(auditLog.actorUserId).isEqualTo(adminId)
        assertThat(auditLog.action).isEqualTo("MEDIA_ASSET_REJECTED")
        assertThat(auditLog.beforeState).isEqualTo(
            """{"moderationStatus":"UPLOADED","approvedFileUrl":null,"thumbnailUrl":null}""",
        )
        assertThat(auditLog.afterState).isEqualTo(
            """{"moderationStatus":"REJECTED","approvedFileUrl":null,"thumbnailUrl":null}""",
        )
    }

    @Test
    fun `hides approved media asset`() {
        val approved = uploadedMediaAsset().approve(
            approvedFileUrl = "https://cdn.example.test/approved.png",
            thumbnailUrl = null,
            now = now.plusSeconds(1),
        )
        val repository = FakeMediaAssetRepository(assets = mutableListOf(approved))
        val auditLogPort = FakeAuditLogPort()
        val useCase = HideMediaAsset(
            transactionPort = ImmediateTransactionPort,
            mediaAssetRepository = repository,
            auditLogPort = auditLogPort,
            clockPort = ClockPort { now.plusSeconds(10) },
            idGenerator = { UUID.fromString("00000000-0000-0000-0000-000000003103") },
        )

        val result = useCase.hide(
            HideMediaAsset.Command(
                adminId = adminId,
                mediaAssetId = mediaAssetId,
            ),
        )

        assertThat(result.moderationStatus).isEqualTo(ModerationStatus.HIDDEN)
        assertThat(result.isPubliclyVisible).isFalse()
        val auditLog = auditLogPort.appended.single()
        assertThat(auditLog.id).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000003103"))
        assertThat(auditLog.actorUserId).isEqualTo(adminId)
        assertThat(auditLog.action).isEqualTo("MEDIA_ASSET_HIDDEN")
        assertThat(auditLog.beforeState).isEqualTo(
            """{"moderationStatus":"APPROVED","approvedFileUrl":"https://cdn.example.test/approved.png","thumbnailUrl":null}""",
        )
        assertThat(auditLog.afterState).isEqualTo(
            """{"moderationStatus":"HIDDEN","approvedFileUrl":"https://cdn.example.test/approved.png","thumbnailUrl":null}""",
        )
    }

    @Test
    fun `rejects hiding unapproved media asset`() {
        val repository = FakeMediaAssetRepository(assets = mutableListOf(uploadedMediaAsset()))
        val useCase = HideMediaAsset(
            transactionPort = ImmediateTransactionPort,
            mediaAssetRepository = repository,
            auditLogPort = FakeAuditLogPort(),
            clockPort = ClockPort { now.plusSeconds(10) },
        )

        assertThatIllegalArgumentException()
            .isThrownBy {
                useCase.hide(
                    HideMediaAsset.Command(
                        adminId = adminId,
                        mediaAssetId = mediaAssetId,
                    ),
                )
            }
            .withMessage("media asset is not hideable")
    }

    private fun uploadedMediaAsset(): MediaAsset =
        MediaAsset.uploaded(
            id = mediaAssetId,
            ownershipRecordId = UUID.fromString("00000000-0000-0000-0000-000000003003"),
            ownerId = UUID.fromString("00000000-0000-0000-0000-000000003004"),
            mediaType = MediaType.IMAGE,
            originalFileUrl = "https://cdn.example.test/original.png",
            now = now,
        )

    private object ImmediateTransactionPort : TransactionPort {
        override fun <T> execute(block: () -> T): T = block()
    }

    private class FakeAuditLogPort : AuditLogPort {
        val appended = mutableListOf<AuditLog>()

        override fun append(log: AuditLog): AuditLog {
            appended.add(log)
            return log
        }
    }

    private class FakeMediaAssetRepository(
        private val assets: MutableList<MediaAsset>,
    ) : MediaAssetRepository {
        val updated = mutableListOf<MediaAsset>()

        override fun save(asset: MediaAsset): MediaAsset {
            assets.add(asset)
            return asset
        }

        override fun update(asset: MediaAsset): MediaAsset {
            assets.replaceAll { if (it.id == asset.id) asset else it }
            updated.add(asset)
            return asset
        }

        override fun findById(id: UUID): MediaAsset? = assets.find { it.id == id }

        override fun findByIdForUpdate(id: UUID): MediaAsset? = findById(id)

        override fun findByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> =
            assets.filter { it.ownershipRecordId == ownershipRecordId }

        override fun findApprovedByOwnershipRecordId(ownershipRecordId: UUID): List<MediaAsset> =
            assets.filter { it.ownershipRecordId == ownershipRecordId && it.moderationStatus == ModerationStatus.APPROVED }

        override fun findByModerationStatus(status: ModerationStatus): List<MediaAsset> =
            assets.filter { it.moderationStatus == status }

        override fun findByOwnerId(ownerId: UUID): List<MediaAsset> =
            assets.filter { it.ownerId == ownerId }
    }
}
