package com.timearchive.domain.port

import com.timearchive.domain.model.UserAccount
import java.util.UUID

interface UserAccountRepository {
    fun save(user: UserAccount): UserAccount

    fun findById(id: UUID): UserAccount?

    fun findByNormalizedEmail(normalizedEmail: String): UserAccount?
}
