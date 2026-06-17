package com.timearchive.application

import com.timearchive.domain.model.MediaType
import com.timearchive.domain.model.PublicTimelineSegment
import com.timearchive.domain.model.TimeRange
import com.timearchive.domain.port.PublicTimelineSegmentRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.util.UUID

class ListPublicTimelineSegmentsTest {
    @Test
    fun `lists public timeline segments for requested range`() {
        val segment = segment(startSecond = 10, endSecond = 20)
        val repository = FakePublicTimelineSegmentRepository(segments = listOf(segment))
        val useCase = ListPublicTimelineSegments(publicTimelineSegmentRepository = repository)

        val result = useCase.list(ListPublicTimelineSegments.Query(from = 0, to = 300))

        assertThat(result.range).isEqualTo(TimeRange(startSecond = 0, endSecond = 300))
        assertThat(result.segments).containsExactly(segment)
        assertThat(repository.requestedRange).isEqualTo(TimeRange(startSecond = 0, endSecond = 300))
    }

    @Test
    fun `rejects range outside canonical timeline`() {
        val useCase = ListPublicTimelineSegments(
            publicTimelineSegmentRepository = FakePublicTimelineSegmentRepository(),
        )

        assertThatIllegalArgumentException()
            .isThrownBy {
                useCase.list(ListPublicTimelineSegments.Query(from = 86_399, to = 86_401))
            }
            .withMessage("endSecond must be less than or equal to totalSeconds")
    }

    @Test
    fun `rejects invalid range ordering`() {
        val useCase = ListPublicTimelineSegments(
            publicTimelineSegmentRepository = FakePublicTimelineSegmentRepository(),
        )

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
            mediaUrl = "https://cdn.example.com/approved.png",
            thumbnailUrl = "https://cdn.example.com/thumb.png",
            externalLink = "https://example.com",
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
}
