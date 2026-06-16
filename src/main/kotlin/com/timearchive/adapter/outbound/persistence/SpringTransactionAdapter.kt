package com.timearchive.adapter.outbound.persistence

import com.timearchive.domain.port.TransactionPort
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class SpringTransactionAdapter(
    private val transactionTemplate: TransactionTemplate,
) : TransactionPort {
    override fun <T> execute(block: () -> T): T =
        transactionTemplate.execute { block() }
            ?: error("transaction returned null")
}
