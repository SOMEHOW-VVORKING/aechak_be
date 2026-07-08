package com.aechak.application.order.usecase.command

/**
 * 주문 생성(쓰기) 입력 골격. Command/Query/스칼라 인자 구분 규칙과 예시는
 * user 템플릿(UserUseCase·RegisterUserCommand·UserSearchQuery) 참조.
 */
data class PlaceOrderCommand(
    val buyerId: Long,
    // TODO: 기능 확정 시 필드 추가 (품목·배송지·적립금 사용 등)
)
