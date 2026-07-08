package com.aechak.domain.product

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 상품 애그리거트 루트 — 골격. ERD(상품·리뷰 BC: 카테고리·옵션·재고·찜·리뷰 등) 반영 시 채운다.
 * 엔티티 작성 규칙은 user 도메인 템플릿(User) 참조.
 * 재고처럼 동시성 정합성이 걸린 규칙은 엔티티가 아니라 저장소 레벨(조건부 원자 UPDATE)로 강제한다.
 */
@Entity
@Table(name = "products")
class Product protected constructor() : AggregateRoot() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    // TODO: ERD 반영 시 필드·상태 전이 메서드 정의
}
