package com.wangzan101.mika.core.uistate

internal interface UntilWaitPort {
    suspend fun waitStable(timeoutMs: Long): Boolean
    suspend fun awaitChange(timeoutMs: Long): Boolean
}
