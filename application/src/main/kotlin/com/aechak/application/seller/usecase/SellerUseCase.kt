package com.aechak.application.seller.usecase

import com.aechak.domain.seller.seller.enums.SellerStatus

/**
 * seller 도메인의 유일한 진입점 계약. 규칙은 user 도메인 템플릿(UserUseCase) 참조.
 * 입출력 어휘(command/·result/·query/)는 이 패키지 하위에 둔다 — 계약은 usecase/, 구현은 service/.
 */
interface SellerUseCase {
    /**
     * 셀러 자격 게이트. 셀러로 등록된 적이 없으면 10004(403)로 거절하고, 등록돼 있으면 그 상태를 돌려준다.
     * 어느 상태까지 허용할지는 소비하는 도메인이 정한다.
     *
     * 불린을 돌려주지 않는 이유는 "셀러가 아니다"와 "활성이 아니다"가 호출자에게서 합쳐지기 때문이다 —
     * 합치면 입점한 적 없는 계정에 정지 안내가 나간다.
     */
    fun requireSellerStatus(userId: Long): SellerStatus

    /**
     * 셀러 상태. 셀러로 등록된 적이 없으면 null이다.
     * 탈퇴 판정처럼 셀러가 아니어도 되는 조회에 쓰고 자격 게이트는 requireSellerStatus를 쓴다.
     */
    fun findSellerStatus(userId: Long): SellerStatus?
}
