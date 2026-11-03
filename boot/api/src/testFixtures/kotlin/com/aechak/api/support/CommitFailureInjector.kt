package com.aechak.api.support

import org.springframework.transaction.TransactionExecution
import org.springframework.transaction.TransactionExecutionListener

/**
 * 커밋은 끝났는데 호출자가 예외를 받는 경우(커밋 응답을 받기 전에 연결이 끊긴 경우)를 흉내 낸다.
 * 컨텍스트가 공유하는 빈이라 failAfterFirstWriteCommit 블록을 실행하는 스레드의 첫 쓰기 커밋에만 동작한다.
 */
class CommitFailureInjector : TransactionExecutionListener {
    private val armed = ThreadLocal<Boolean>()

    fun <T> failAfterFirstWriteCommit(block: () -> T): T {
        armed.set(true)
        try {
            return block()
        } finally {
            armed.remove()
        }
    }

    override fun afterCommit(
        transaction: TransactionExecution,
        commitFailure: Throwable?,
    ) {
        if (commitFailure == null && !transaction.isReadOnly && armed.get() == true) {
            armed.remove()
            throw IllegalStateException("커밋 응답 유실을 흉내 낸 예외")
        }
    }
}
