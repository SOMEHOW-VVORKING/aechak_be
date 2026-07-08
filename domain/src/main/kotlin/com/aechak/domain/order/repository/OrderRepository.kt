package com.aechak.domain.order.repository

import com.aechak.domain.order.Order

/**
 * order 애그리거트 저장 포트. 구현은 infra 어댑터가 담당한다.
 * 시그니처는 도메인 타입만 사용한다 — Spring 타입 노출 금지.
 */
interface OrderRepository {
    fun findById(id: Long): Order?
    fun save(order: Order): Order
}
