package com.timearchive.application

import com.timearchive.domain.model.UserAccount
import com.timearchive.domain.port.UserAccountRepository
import java.util.UUID

class GetCurrentUser(
    private val userAccountRepository: UserAccountRepository,
) {
    fun get(query: Query): UserAccount =
        userAccountRepository.findById(query.userId)
            ?: throw IllegalStateException("current user not found")

    data class Query(
        val userId: UUID,
    )
}
