package com.aechak.domain.support

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.LocalDateTime

/**
 * 전 엔티티 공통 시각 컬럼(created_at/updated_at) 베이스.
 * 전 테이블 공통 규약(2026-07-10 결정) — ERD에 updated_at이 없던 테이블도 공통으로 갖는다.
 * common 모듈이 아닌 이곳에 두는 이유: common은 무의존 순수 모듈이라 jakarta.persistence를 들일 수 없다(05 문서).
 * Spring Data Auditing은 domain의 Spring 금지 규칙 때문에 쓰지 않는다 — 순수 JPA 콜백으로 갱신.
 * [한계] JPQL/native 원자 UPDATE는 dirty checking을 우회해 @PreUpdate가 돌지 않는다 —
 * 원자 UPDATE 쿼리는 반드시 `updated_at = NOW()`를 함께 SET해야 한다(리포지토리 어댑터 규약).
 */
@MappedSuperclass
abstract class BaseEntity {

    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
        protected set

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    @PrePersist
    protected fun prePersistBase() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    protected fun preUpdateBase() {
        updatedAt = LocalDateTime.now()
    }
}
