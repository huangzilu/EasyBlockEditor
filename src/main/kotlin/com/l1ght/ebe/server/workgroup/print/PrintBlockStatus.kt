package com.l1ght.ebe.server.workgroup.print

enum class PrintBlockStatus(val terminal: Boolean) {
    PENDING(false),
    RESERVED(false),
    PLACED(true),
    FAILED_MISSING_MATERIAL(true),
    FAILED_BLOCKED(true),
    CANCELLED(true)
}
