package com.aechak.application.product.product.usecase.command

/**
 * 상품 등록(쓰기) 입력 골격. Command/Query/스칼라 인자 구분 규칙과 예시는
 * user 템플릿(UserUseCase·RegisterUserCommand·UserSearchQuery) 참조.
 */
data class RegisterProductCommand(
    val sellerId: Long,
    // TODO: 기능 확정 시 필드 추가 (옵션·재고·할인 등)
)
