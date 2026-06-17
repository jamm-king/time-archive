package com.timearchive.domain.port

import com.timearchive.domain.model.OutboxEvent

interface OutboxPort {
    fun append(event: OutboxEvent): OutboxEvent
}
