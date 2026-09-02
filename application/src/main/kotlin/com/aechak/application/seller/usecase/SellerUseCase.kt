package com.aechak.application.seller.usecase

import com.aechak.domain.seller.seller.enums.SellerStatus

/**
 * seller 도메인의 유일한 진입점 계약. 규칙은 user 도메인 템플릿(UserUseCase) 참조.
 * 입출력 어휘(command/·result/·query/)는 이 패키지 하위에 둔다 — 계약은 usecase/, 구현은 service/.
 */
interface SellerUseCase {
    fun isActiveSeller(userId: Long): Boolean

    /** 셀러 상태 — 셀러가 아니면 null. 상태별 허용 정책은 소비하는 도메인이 정한다. */
    fun getSellerStatus(userId: Long): SellerStatus?
}
