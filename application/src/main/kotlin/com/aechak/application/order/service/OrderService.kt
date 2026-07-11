package com.aechak.application.order.service

import org.springframework.stereotype.Service
import com.aechak.domain.order.order.repository.OrderRepository

/**
 * order 도메인 비즈니스 로직 보관함 — Facade에서만 호출된다.
 * 리포지토리는 domain의 포트(OrderRepository)를 주입받는다 — 어댑터가 생기는 시점에 연결.
 */
@Service
class OrderService {
    // TODO: 기능 구현 시 로직 추가
}
