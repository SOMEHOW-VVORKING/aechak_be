package com.aechak.domain.order

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 주문 애그리거트 루트 — 골격. ERD(주문·배송·클레임 BC: 주문그룹·품목·상태이력·재고예약 등) 반영 시 채운다.
 * 엔티티 작성 규칙은 user 도메인 템플릿(User) 참조.
 * 주문 상태머신(7종)의 상태 전이 규칙은 의도가 드러나는 메서드(cancel, confirm...)로만 연다.
 */
@Entity
@Table(name = "orders")
class Order protected constructor() : AggregateRoot() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    // TODO: ERD 반영 시 필드·상태 전이 메서드 정의
}
