package com.aechak.application.support

import org.springframework.dao.CannotAcquireLockException

/** 트랜잭션 하나를 열고 닫는 block을 MySQL이 교착으로 되돌리면 한 번 다시 실행한다 */
fun <T> retryOnceOnDeadlock(block: () -> T): T =
    try {
        block()
    } catch (e: CannotAcquireLockException) {
        block()
    }
