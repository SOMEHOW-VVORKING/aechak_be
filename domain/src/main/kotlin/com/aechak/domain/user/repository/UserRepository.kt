package com.aechak.domain.user.repository

import com.aechak.domain.user.User

/**
 * user 애그리거트 저장 포트. 구현은 infra(persistence 등)의 어댑터가 담당한다 —
 * 저장 기술 교체(예: Elasticsearch 도입)를 어댑터 교체만으로 흡수하기 위한 구조다.
 *
 * 규칙:
 * - 시그니처는 도메인 타입만 사용한다. Pageable 등 Spring 타입 노출 금지.
 * - 메서드는 애그리거트 로딩/저장 중심으로 최소하게 유지한다. 복잡한 조회는 별도 논의.
 */
interface UserRepository {
    fun findById(id: Long): User?
    fun save(user: User): User
}
