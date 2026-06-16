package com.timearchive.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class ArchiveTimelineTest {
    @Test
    fun `uses one canonical 24 hour timeline`() {
        assertThat(ArchiveTimeline.TOTAL_SECONDS).isEqualTo(86_400)
    }

    @Test
    fun `accepts range within canonical timeline`() {
        val range = TimeRange(startSecond = 0, endSecond = 86_400)

        assertThat(ArchiveTimeline.requireWithin(range)).isSameAs(range)
    }

    @Test
    fun `rejects range beyond canonical timeline`() {
        assertThatIllegalArgumentException()
            .isThrownBy { ArchiveTimeline.requireWithin(TimeRange(startSecond = 86_399, endSecond = 86_401)) }
            .withMessage("endSecond must be less than or equal to totalSeconds")
    }
}
