package com.aechak.application.seller.usecase

import com.aechak.application.seller.usecase.command.UpdateShippingPolicyCommand
import com.aechak.application.seller.usecase.result.ShippingPolicyResult

/**
 * 셀러센터 배송 정책 진입점 계약. 타 BC가 쓰는 SellerUseCase와 나눠 둔다 —
 * 소비하는 실행 모듈이 셀러센터 하나이고 메서드가 겹치지 않는다.
 */
interface SellerShippingPolicyUseCase {
    /** 내 배송 정책 — 미설정 항목은 컬럼 기본값으로 내려간다. */
    fun getShippingPolicy(sellerId: Long): ShippingPolicyResult

    /** 내 배송 정책 전체 덮어쓰기. */
    fun updateShippingPolicy(command: UpdateShippingPolicyCommand)
}
