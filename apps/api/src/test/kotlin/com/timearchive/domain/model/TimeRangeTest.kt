package com.timearchive.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class TimeRangeTest {
    @Test
    fun `creates range with inclusive start and exclusive end`() {
        val range = TimeRange(startSecond = 0, endSecond = 10)

        assertThat(range.durationSeconds).isEqualTo(10)
        assertThat(range.contains(0)).isTrue()
        assertThat(range.contains(9)).isTrue()
        assertThat(range.contains(10)).isFalse()
    }

    @Test
    fun `rejects negative start second`() {
        assertThatIllegalArgumentException()
            .isThrownBy { TimeRange(startSecond = -1, endSecond = 10) }
            .withMessage("startSecond must be greater than or equal to 0")
    }

    @Test
    fun `rejects end second that is not greater than start second`() {
        assertThatIllegalArgumentException()
            .isThrownBy { TimeRange(startSecond = 10, endSecond = 10) }
            .withMessage("endSecond must be greater than startSecond")
    }

    @Test
    fun `detects overlapping ranges`() {
        val range = TimeRange(startSecond = 10, endSecond = 20)

        assertThat(range.overlaps(TimeRange(startSecond = 0, endSecond = 10))).isFalse()
        assertThat(range.overlaps(TimeRange(startSecond = 0, endSecond = 11))).isTrue()
        assertThat(range.overlaps(TimeRange(startSecond = 19, endSecond = 30))).isTrue()
        assertThat(range.overlaps(TimeRange(startSecond = 20, endSecond = 30))).isFalse()
    }

    @Test
    fun `requires range to fit within archive duration`() {
        val range = TimeRange(startSecond = 90, endSecond = 100)

        assertThat(range.requireWithin(totalSeconds = 100)).isSameAs(range)
    }

    @Test
    fun `rejects range beyond archive duration`() {
        assertThatIllegalArgumentException()
            .isThrownBy { TimeRange(startSecond = 90, endSecond = 101).requireWithin(totalSeconds = 100) }
            .withMessage("endSecond must be less than or equal to totalSeconds")
    }
}
