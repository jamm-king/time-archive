package com.timearchive.application

import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.PublicTimelineSegment
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.ClockPort
import com.timearchive.domain.port.MediaObjectStoragePort
import com.timearchive.domain.port.PublicTimelineSegmentRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ListPublicTimelineSegmentsTest {
    private val now: Instant = Instant.parse("2026-06-19T00:00:00Z")

    @Test
    fun `lists public timeline segments with presigned media urls for requested range`() {
        val segment = segment(startSecond = 10, endSecond = 20)
        val repository = FakePublicTimelineSegmentRepository(segments = listOf(segment))
        val storage = FakeMediaObjectStoragePort()
        val useCase = useCase(repository = repository, storage = storage)

        val result = useCase.list(ListPublicTimelineSegments.Query(from = 0, to = 300))

        assertThat(result.range).isEqualTo(TimeRange(startSecond = 0, endSecond = 300))
        assertThat(result.segments).containsExactly(
            segment.copy(
                mediaUrl = "https://storage.example.test/presigned/1",
                thumbnailUrl = "https://storage.example.test/presigned/2",
            ),
        )
        assertThat(repository.requestedRange).isEqualTo(TimeRange(startSecond = 0, endSecond = 300))
        assertThat(storage.downloadCommands).containsExactly(
            MediaObjectStoragePort.DownloadCommand(
                fileUrl = "https://storage.example.test/approved.png",
                expiresAt = now.plusSeconds(600),
            ),
            MediaObjectStoragePort.DownloadCommand(
                fileUrl = "https://storage.example.test/thumb.png",
                expiresAt = now.plusSeconds(600),
            ),
        )
    }

    @Test
    fun `rejects range outside canonical timeline`() {
        val useCase = useCase()

        assertThatIllegalArgumentException()
            .isThrownBy {
                useCase.list(ListPublicTimelineSegments.Query(from = 86_399, to = 86_401))
            }
            .withMessage("endSecond must be less than or equal to totalSeconds")
    }

    @Test
    fun `rejects invalid range ordering`() {
        val useCase = useCase()

        assertThatIllegalArgumentException()
            .isThrownBy {
                useCase.list(ListPublicTimelineSegments.Query(from = 20, to = 20))
            }
            .withMessage("endSecond must be greater than startSecond")
    }

    private fun segment(startSecond: Long, endSecond: Long): PublicTimelineSegment =
        PublicTimelineSegment(
            range = TimeRange(startSecond = startSecond, endSecond = endSecond),
            mediaAssetId = UUID.fromString("00000000-0000-0000-0000-000000000901"),
            mediaType = MediaType.IMAGE,
            mediaUrl = "https://storage.example.test/approved.png",
            thumbnailUrl = "https://storage.example.test/thumb.png",
            externalLink = "https://example.com",
        )

    private fun useCase(
        repository: PublicTimelineSegmentRepository = FakePublicTimelineSegmentRepository(),
        storage: MediaObjectStoragePort = FakeMediaObjectStoragePort(),
    ): ListPublicTimelineSegments =
        ListPublicTimelineSegments(
            publicTimelineSegmentRepository = repository,
            mediaObjectStoragePort = storage,
            clockPort = ClockPort { now },
            playbackUrlTtl = Duration.ofSeconds(600),
        )

    private class FakePublicTimelineSegmentRepository(
        private val segments: List<PublicTimelineSegment> = emptyList(),
    ) : PublicTimelineSegmentRepository {
        var requestedRange: TimeRange? = null

        override fun findApprovedOverlapping(range: TimeRange): List<PublicTimelineSegment> {
            requestedRange = range
            return segments
        }
    }

    private class FakeMediaObjectStoragePort : MediaObjectStoragePort {
        val downloadCommands = mutableListOf<MediaObjectStoragePort.DownloadCommand>()

        override fun createPresignedUpload(command: MediaObjectStoragePort.Command): MediaObjectStoragePort.PresignedUpload =
            error("not used")

        override fun createPresignedDownload(
            command: MediaObjectStoragePort.DownloadCommand,
        ): MediaObjectStoragePort.PresignedDownload {
            downloadCommands.add(command)
            return MediaObjectStoragePort.PresignedDownload(
                downloadUrl = "https://storage.example.test/presigned/${downloadCommands.size}",
                expiresAt = command.expiresAt,
            )
        }

        override fun isManagedFileUrl(fileUrl: String): Boolean = true

        override fun findObjectMetadata(objectKey: String): MediaObjectStoragePort.ObjectMetadata? = null

        override fun openObject(objectKey: String): java.io.InputStream? = null
    }
}
