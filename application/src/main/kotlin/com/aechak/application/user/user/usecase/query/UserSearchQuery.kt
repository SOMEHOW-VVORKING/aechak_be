package com.aechak.application.user.user.usecase.query

/**
 * 복잡 조회 입력 — Query 객체 승격 기준을 보여주는 템플릿.
 *
 * - 단순 조회(식별자 등 스칼라 1~2개)는 이 객체를 만들지 않고 함수 인자로 받는다 (UserUseCase.getUser 참조).
 * - 조건 3개 이상 / 페이징 / 옵셔널 필터 조합이 등장하는 순간 {대상}SearchQuery로 승격한다.
 * - 쓰기 입력(Command)과 섞지 않는다 — 쓰기는 Command, 조회는 Query로 분리.
 */
data class UserSearchQuery(
    val nickname: String? = null, // 옵셔널 필터
    val page: Int = 0,
    val size: Int = 20,
)
