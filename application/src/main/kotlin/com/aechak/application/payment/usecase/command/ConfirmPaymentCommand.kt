package com.aechak.application.payment.usecase.command

/**
 * 결제 승인(쓰기) 입력 골격. Command/Query/스칼라 인자 구분 규칙과 예시는
 * user 템플릿(UserUseCase·RegisterUserCommand·UserSearchQuery) 참조.
 */
data class ConfirmPaymentCommand(
    val orderId: Long,
    // TODO: 기능 확정 시 필드 추가 (PG 승인 키·금액 검증·멱등 키 등)
)
