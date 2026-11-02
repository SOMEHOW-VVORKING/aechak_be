package com.aechak.application.auth.port

import com.aechak.domain.user.user.enums.UserRole

/**
 * 토큰 회전 전용 역할 조회 포트.
 *
 * [UserStatusReader]와 가르는 이유: 그쪽은 매 인증 요청을 거치는 상태검증 필터가 쓰는 경량 포트이고,
 * 그 경로는 역할을 필요로 하지 않는다. 역할은 refresh 회전 시점에만 다시 읽으면 되고 회전은
 * access 만료 주기로만 일어나므로, 호출 빈도가 다른 둘을 한 포트에 묶지 않는다.
 *
 * domain 포트(Repository류)가 아닌 이유도 같다 — 애그리거트 계약이 아니라 토큰 발급이 필요로 하는
 * read-model 프로젝션이며 소비자(auth)가 정의하는 계약이다.
 */
interface UserRoleReader {
    /**
     * 발급에 쓸 현재 역할.
     *
     * null = 유저 없음. 호출부는 **토큰에 박힌 역할로 되돌아가지 말고** 차단으로 취급해야 한다 —
     * 그러지 않으면 탈퇴·삭제된 계정의 옛 refresh가 자기가 들고 있던 역할을 계속 되살린다.
     */
    fun roleOf(userId: Long): UserRole?
}
