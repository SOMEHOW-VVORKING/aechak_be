package com.aechak.application.seller.usecase.command

/**
 * 입점 신청(쓰기) 입력 골격. Command/Query/스칼라 인자 구분 규칙과 예시는
 * user 템플릿(UserUseCase·RegisterUserCommand·UserSearchQuery) 참조.
 */
data class ApplySellerCommand(
    val userId: Long,
    // TODO: 기능 확정 시 필드 추가 (유형·계좌·서류 등)
)
