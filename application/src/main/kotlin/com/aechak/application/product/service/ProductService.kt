package com.aechak.application.product.service

import org.springframework.stereotype.Service

/**
 * product 도메인 비즈니스 로직 보관함 — Facade에서만 호출된다.
 * 리포지토리는 domain의 포트(ProductRepository)를 주입받는다 — 어댑터가 생기는 시점에 연결.
 * 재고 차감 등 동시성 규칙은 저장소 레벨 강제(조건부 원자 UPDATE)를 우선한다.
 */
@Service
class ProductService {
    // TODO: 기능 구현 시 로직 추가
}
