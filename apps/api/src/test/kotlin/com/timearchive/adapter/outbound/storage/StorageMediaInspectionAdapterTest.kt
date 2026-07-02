package com.timearchive.adapter.outbound.storage

import com.timearchive.domain.port.MediaObjectStoragePort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant

class StorageMediaInspectionAdapterTest {
    @Test
    fun `inspects matching image signature without duration`() {
        val adapter = adapterWith(
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4e,
                0x47,
                0x0d,
                0x0a,
                0x1a,
                0x0a,
            ),
        )

        val result = adapter.inspect(command(contentType = "image/png"))

        assertThat(result.signatureMatchesContentType).isTrue()
        assertThat(result.durationMs).isNull()
    }

    @Test
    fun `inspects mismatched image signature`() {
        val adapter = adapterWith("not-a-png".toByteArray(Charsets.US_ASCII))

        val result = adapter.inspect(command(contentType = "image/png"))

        assertThat(result.signatureMatchesContentType).isFalse()
        assertThat(result.durationMs).isNull()
    }

    @Test
    fun `inspects matching mp4 signature and duration`() {
        val adapter = adapterWith(mp4WithMovieHeader(durationMs = 1_000))

        val result = adapter.inspect(command(contentType = "video/mp4"))

        assertThat(result.signatureMatchesContentType).isTrue()
        assertThat(result.durationMs).isEqualTo(1_000)
    }

    private fun adapterWith(bytes: ByteArray): StorageMediaInspectionAdapter =
        StorageMediaInspectionAdapter(
            mediaObjectStoragePort = FakeMediaObjectStoragePort(bytes),
        )

    private fun command(contentType: String) =
        com.timearchive.domain.port.MediaInspectionPort.Command(
            objectKey = "media/originals/object",
            contentType = contentType,
        )

    private fun mp4WithMovieHeader(durationMs: Long): ByteArray =
        concat(
            box("ftyp", "isom\u0000\u0000\u0002\u0000isommp41".toByteArray(Charsets.US_ASCII)),
            box("moov", box("mvhd", movieHeaderPayload(durationMs))),
        )

    private fun movieHeaderPayload(durationMs: Long): ByteArray =
        concat(
            byteArrayOf(0, 0, 0, 0),
            uint32(0),
            uint32(0),
            uint32(1_000),
            uint32(durationMs),
        )

    private fun box(type: String, payload: ByteArray): ByteArray =
        concat(
            uint32(payload.size + 8L),
            type.toByteArray(Charsets.US_ASCII),
            payload,
        )

    private fun uint32(value: Long): ByteArray =
        byteArrayOf(
            ((value ushr 24) and 0xff).toByte(),
            ((value ushr 16) and 0xff).toByte(),
            ((value ushr 8) and 0xff).toByte(),
            (value and 0xff).toByte(),
        )

    private fun concat(vararg parts: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        parts.forEach(output::writeBytes)
        return output.toByteArray()
    }

    private class FakeMediaObjectStoragePort(
        private val bytes: ByteArray,
    ) : MediaObjectStoragePort {
        override fun createPresignedUpload(command: MediaObjectStoragePort.Command): MediaObjectStoragePort.PresignedUpload =
            error("not used")

        override fun createPresignedDownload(
            command: MediaObjectStoragePort.DownloadCommand,
        ): MediaObjectStoragePort.PresignedDownload = error("not used")

        override fun isManagedFileUrl(fileUrl: String): Boolean = true

        override fun findObjectMetadata(objectKey: String): MediaObjectStoragePort.ObjectMetadata? = null

        override fun openObject(objectKey: String): InputStream = ByteArrayInputStream(bytes)
    }
}
