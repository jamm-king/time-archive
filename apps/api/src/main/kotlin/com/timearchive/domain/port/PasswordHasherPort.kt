package com.timearchive.domain.port

interface PasswordHasherPort {
    fun hash(rawPassword: String): String

    fun matches(rawPassword: String, passwordHash: String): Boolean
}
