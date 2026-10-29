package com.aechak.application.auth.port

import com.aechak.domain.user.user.enums.UserStatus

/**
 * 인가 상태검증 전용 경량 조회 포트 — 매 인증 요청 경로에서 불리므로
 * User 애그리거트 로딩 없이 status 프로젝션 단발 쿼리로 구현한다.
 *
 * domain 포트(Repository류)가 아닌 이유: 애그리거트 계약이 아니라 인가 게이트 전용
 * read-model 프로젝션이며, 소비자(auth: 필터·토큰 회전)가 정의하는 계약이다.
 * 트래픽 증가 시 Redis 캐시 데코레이터를 이 포트 뒤에 끼운다 — 호출부 무변경(확장 사다리).
 */
interface UserStatusReader {
    /**
     * 접근 판정에 쓸 **유효 상태** — 기간이 지난 임시 정지는 ACTIVE로 돌려준다.
     * 만료 판정을 호출부마다 반복하지 않도록 포트가 책임지며, 규칙 자체는 도메인(UserStatus.effective)이 소유한다.
     *
     * null = 유저 없음(탈퇴 직후 옛 토큰 등) — 호출부는 차단으로 취급한다.
     */
    fun statusOf(userId: Long): UserStatus?
}
