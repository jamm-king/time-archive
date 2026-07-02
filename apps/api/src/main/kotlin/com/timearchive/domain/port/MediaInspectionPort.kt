package com.timearchive.domain.port

interface MediaInspectionPort {
    fun inspect(command: Command): Result

    data class Command(
        val objectKey: String,
        val contentType: String,
    )

    data class Result(
        val durationMs: Long?,
    )
}
