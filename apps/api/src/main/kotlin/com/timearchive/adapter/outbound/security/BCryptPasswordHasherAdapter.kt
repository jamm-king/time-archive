package com.timearchive.adapter.outbound.security

import com.timearchive.domain.port.PasswordHasherPort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordHasherAdapter(
    private val passwordEncoder: PasswordEncoder,
) : PasswordHasherPort {
    override fun hash(rawPassword: String): String =
        requireNotNull(passwordEncoder.encode(rawPassword)) { "password hash must not be null" }

    override fun matches(rawPassword: String, passwordHash: String): Boolean =
        passwordEncoder.matches(rawPassword, passwordHash)
}
