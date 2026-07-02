package com.timearchive.adapter.outbound.storage

import java.io.EOFException
import java.io.InputStream

class Mp4DurationParser {
    fun parseDurationMs(input: InputStream): Long {
        while (true) {
            val header = readBoxHeaderOrNull(input) ?: break
            require(header.payloadSize >= 0) { "invalid MP4 box size" }

            if (header.type == "moov") {
                return parseMoovDurationMs(input, header.payloadSize)
            }

            skipFully(input, header.payloadSize)
        }

        throw IllegalArgumentException("MP4 duration metadata not found")
    }

    private fun parseMoovDurationMs(input: InputStream, payloadSize: Long): Long {
        var remaining = payloadSize
        while (remaining > 0) {
            val header = readBoxHeader(input)
            require(header.totalSize <= remaining) { "MP4 child box exceeds parent size" }

            if (header.type == "mvhd") {
                return parseMovieHeaderDurationMs(input, header.payloadSize)
            }

            skipFully(input, header.payloadSize)
            remaining -= header.totalSize
        }

        throw IllegalArgumentException("MP4 movie header not found")
    }

    private fun parseMovieHeaderDurationMs(input: InputStream, payloadSize: Long): Long {
        require(payloadSize >= 20) { "MP4 movie header is too short" }

        val versionAndFlags = readBytes(input, 4)
        val version = versionAndFlags[0].toInt() and 0xff
        return when (version) {
            0 -> {
                require(payloadSize >= 20) { "MP4 version 0 movie header is too short" }
                skipFully(input, 8)
                val timescale = readUInt32(input)
                val duration = readUInt32(input)
                durationToMillis(duration, timescale)
            }
            1 -> {
                require(payloadSize >= 32) { "MP4 version 1 movie header is too short" }
                skipFully(input, 16)
                val timescale = readUInt32(input)
                val duration = readUInt64(input)
                durationToMillis(duration, timescale)
            }
            else -> throw IllegalArgumentException("unsupported MP4 movie header version")
        }
    }

    private fun durationToMillis(duration: Long, timescale: Long): Long {
        require(timescale > 0) { "MP4 timescale must be greater than zero" }
        require(duration >= 0) { "MP4 duration must not be negative" }
        require(duration <= (Long.MAX_VALUE - timescale + 1L) / 1000L) {
            "MP4 duration is too large"
        }
        return (duration * 1000L + timescale - 1L) / timescale
    }

    private fun readBoxHeaderOrNull(input: InputStream): BoxHeader? {
        val prefix = input.readNBytes(8)
        if (prefix.isEmpty()) {
            return null
        }
        require(prefix.size == 8) { "truncated MP4 box header" }
        return parseBoxHeader(input, prefix)
    }

    private fun readBoxHeader(input: InputStream): BoxHeader {
        val prefix = readBytes(input, 8)
        return parseBoxHeader(input, prefix)
    }

    private fun parseBoxHeader(input: InputStream, prefix: ByteArray): BoxHeader {
        val smallSize = readUInt32(prefix, 0)
        val type = prefix.copyOfRange(4, 8).toString(Charsets.US_ASCII)
        require(type.all { it.code in 32..126 }) { "invalid MP4 box type" }

        val totalSize = when (smallSize) {
            0L -> throw IllegalArgumentException("MP4 box with open-ended size is not supported")
            1L -> readUInt64(input)
            else -> smallSize
        }
        val headerSize = if (smallSize == 1L) 16L else 8L
        require(totalSize >= headerSize) { "invalid MP4 box size" }

        return BoxHeader(
            type = type,
            totalSize = totalSize,
            payloadSize = totalSize - headerSize,
        )
    }

    private fun readUInt32(input: InputStream): Long =
        readUInt32(readBytes(input, 4), 0)

    private fun readUInt32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)

    private fun readUInt64(input: InputStream): Long {
        val bytes = readBytes(input, 8)
        require((bytes[0].toInt() and 0x80) == 0) { "MP4 unsigned integer is too large" }
        return bytes.fold(0L) { result, byte -> (result shl 8) or (byte.toLong() and 0xffL) }
    }

    private fun readBytes(input: InputStream, length: Int): ByteArray {
        val bytes = input.readNBytes(length)
        if (bytes.size != length) {
            throw EOFException("unexpected end of MP4 stream")
        }
        return bytes
    }

    private fun skipFully(input: InputStream, length: Long) {
        var remaining = length
        val buffer = ByteArray(DEFAULT_SKIP_BUFFER_SIZE)
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }

            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) {
                throw EOFException("unexpected end of MP4 stream")
            }
            remaining -= read.toLong()
        }
    }

    private data class BoxHeader(
        val type: String,
        val totalSize: Long,
        val payloadSize: Long,
    )

    private companion object {
        const val DEFAULT_SKIP_BUFFER_SIZE = 8192
    }
}
