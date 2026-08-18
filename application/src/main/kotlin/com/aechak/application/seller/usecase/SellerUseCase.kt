package com.aechak.application.seller.usecase

/**
 * seller 도메인의 유일한 진입점 계약. 규칙은 user 도메인 템플릿(UserUseCase) 참조.
 * 입출력 어휘(command/·result/·query/)는 이 패키지 하위에 둔다 — 계약은 usecase/, 구현은 service/.
 */
interface SellerUseCase {
    fun isActiveSeller(userId: Long): Boolean
}
