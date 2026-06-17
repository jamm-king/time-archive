package com.timearchive.domain.port

interface TransactionPort {
    fun <T> execute(block: () -> T): T
}
