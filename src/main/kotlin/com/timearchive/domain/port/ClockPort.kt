package com.timearchive.domain.port

import java.time.Instant

fun interface ClockPort {
    fun now(): Instant
}
