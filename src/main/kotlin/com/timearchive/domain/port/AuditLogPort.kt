package com.timearchive.domain.port

import com.timearchive.domain.model.AuditLog

interface AuditLogPort {
    fun append(log: AuditLog): AuditLog
}
