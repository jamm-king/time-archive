package com.timearchive.adapter.outbound.storage

import java.io.InputStream

class MediaFileSignatureDetector {
    fun matchesContentType(
        input: InputStream,
        contentType: String,
    ): Boolean {
        val header = input.readNBytes(32)
        return when (contentType) {
            "image/jpeg" -> isJpeg(header)
            "image/png" -> isPng(header)
            "image/webp" -> isWebp(header)
            "video/mp4" -> isMp4(header)
            else -> false
        }
    }

    private fun isJpeg(header: ByteArray): Boolean =
        header.size >= 3 &&
            header[0] == 0xff.toByte() &&
            header[1] == 0xd8.toByte() &&
            header[2] == 0xff.toByte()

    private fun isPng(header: ByteArray): Boolean {
        val signature = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4e,
            0x47,
            0x0d,
            0x0a,
            0x1a,
            0x0a,
        )
        return header.size >= signature.size &&
            signature.indices.all { index -> header[index] == signature[index] }
    }

    private fun isWebp(header: ByteArray): Boolean =
        header.size >= 12 &&
            header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
            header.copyOfRange(8, 12).contentEquals("WEBP".toByteArray(Charsets.US_ASCII))

    private fun isMp4(header: ByteArray): Boolean =
        header.size >= 12 &&
            header.copyOfRange(4, 8).contentEquals("ftyp".toByteArray(Charsets.US_ASCII))
}
