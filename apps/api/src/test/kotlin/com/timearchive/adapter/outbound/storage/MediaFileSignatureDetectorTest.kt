package com.timearchive.adapter.outbound.storage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class MediaFileSignatureDetectorTest {
    private val detector = MediaFileSignatureDetector()

    @Test
    fun `matches jpeg signature`() {
        assertThat(matches(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x00), "image/jpeg"))
            .isTrue()
    }

    @Test
    fun `matches png signature`() {
        val bytes = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4e,
            0x47,
            0x0d,
            0x0a,
            0x1a,
            0x0a,
        )

        assertThat(matches(bytes, "image/png")).isTrue()
    }

    @Test
    fun `matches webp signature`() {
        assertThat(matches("RIFFxxxxWEBP".toByteArray(Charsets.US_ASCII), "image/webp")).isTrue()
    }

    @Test
    fun `matches mp4 ftyp signature`() {
        val bytes = byteArrayOf(0x00, 0x00, 0x00, 0x18) +
            "ftypisom".toByteArray(Charsets.US_ASCII)

        assertThat(matches(bytes, "video/mp4")).isTrue()
    }

    @Test
    fun `rejects mismatched signature`() {
        assertThat(matches("not-a-png".toByteArray(Charsets.US_ASCII), "image/png")).isFalse()
    }

    @Test
    fun `rejects unsupported content type`() {
        assertThat(matches(byteArrayOf(1, 2, 3), "image/gif")).isFalse()
    }

    private fun matches(bytes: ByteArray, contentType: String): Boolean =
        detector.matchesContentType(
            input = ByteArrayInputStream(bytes),
            contentType = contentType,
        )
}
