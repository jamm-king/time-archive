package com.timearchive.adapter.outbound.storage

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class Mp4DurationParserTest {
    private val parser = Mp4DurationParser()

    @Test
    fun `parses movie header duration from mp4 stream`() {
        val bytes = mp4WithMovieHeader(timescale = 1_000, duration = 10_000)

        val durationMs = parser.parseDurationMs(ByteArrayInputStream(bytes))

        assertThat(durationMs).isEqualTo(10_000)
    }

    @Test
    fun `rounds duration up to milliseconds`() {
        val bytes = mp4WithMovieHeader(timescale = 30, duration = 1)

        val durationMs = parser.parseDurationMs(ByteArrayInputStream(bytes))

        assertThat(durationMs).isEqualTo(34)
    }

    @Test
    fun `rejects mp4 without movie header`() {
        val bytes = box("moov", box("free", byteArrayOf(1, 2, 3)))

        assertThatIllegalArgumentException()
            .isThrownBy { parser.parseDurationMs(ByteArrayInputStream(bytes)) }
            .withMessage("MP4 movie header not found")
    }

    private fun mp4WithMovieHeader(timescale: Long, duration: Long): ByteArray =
        concat(
            box("ftyp", byteArrayOf(0, 0, 0, 0)),
            box("moov", box("mvhd", movieHeaderPayload(timescale = timescale, duration = duration))),
        )

    private fun movieHeaderPayload(timescale: Long, duration: Long): ByteArray =
        concat(
            byteArrayOf(0, 0, 0, 0),
            uint32(0),
            uint32(0),
            uint32(timescale),
            uint32(duration),
        )

    private fun box(type: String, payload: ByteArray): ByteArray =
        concat(
            uint32(payload.size + 8L),
            type.toByteArray(Charsets.US_ASCII),
            payload,
        )

    private fun uint32(value: Long): ByteArray {
        require(value in 0..4_294_967_295L)
        return byteArrayOf(
            ((value ushr 24) and 0xff).toByte(),
            ((value ushr 16) and 0xff).toByte(),
            ((value ushr 8) and 0xff).toByte(),
            (value and 0xff).toByte(),
        )
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        parts.forEach(output::writeBytes)
        return output.toByteArray()
    }
}
