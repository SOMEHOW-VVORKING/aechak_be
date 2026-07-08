package com.aechak.domain.seller

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 셀러 애그리거트 루트 — 골격. ERD(셀러 BC: sellers·입점 신청·서류·심사) 반영 시 필드·연관을 채운다.
 * 엔티티 작성 규칙은 user 도메인 템플릿(User) 참조.
 */
@Entity
@Table(name = "sellers")
class Seller protected constructor() : AggregateRoot() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    // TODO: ERD 반영 시 필드·상태 전이 메서드 정의
}
