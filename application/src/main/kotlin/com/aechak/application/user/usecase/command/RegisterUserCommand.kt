package com.aechak.application.user.usecase.command

/**
 * 쓰기 유스케이스 입력 — 인자 개수와 무관하게 항상 Command 객체로 받는다
 * (요구사항이 늘면 시그니처 대신 필드가 늘어나 호출부 전파가 최소화된다).
 *
 * - 필드는 이 유스케이스 수행에 필요한 전부이자 최소 — 안 쓰는 필드를 실어 나르지 않는다.
 * - 형식 검증 어노테이션 금지. 형식 검증(@Valid)은 실행 모듈에서 끝났다는 것이 이 객체의 계약이다.
 */
data class RegisterUserCommand(
    val nickname: String,
    // TODO: 기능 구현 시 필드 추가
)
