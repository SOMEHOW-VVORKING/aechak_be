package com.aechak.application.settlement.usecase.command

import com.aechak.domain.settlement.feepolicy.SettlementFeePolicy
import com.aechak.domain.settlement.settlement.Settlement
import java.time.LocalDate

/**
 * 구매확정된 주문 하나의 정산 원장 생성 명세. 금액은 주문 도메인이 계산해 넘기고,
 * 수수료와 지급 예정일은 정산 도메인이 요율 정책으로 계산한다.
 * confirmedOn은 구매확정일이다 — 정산월과 지급 예정일이 전부 여기서 파생되므로 재실행해도 같은 값이 나온다.
 */
data class CreateSettlementLedgerCommand(
    val sellerId: Long,
    val orderId: Long,
    val confirmedOn: LocalDate,
    val sellerShippingFee: Long,
    val items: List<Item>,
) {
    /** grossAmount는 할인 안분액을 뺀 품목 상품금액이다(배송비 제외). */
    data class Item(
        val orderItemId: Long,
        val grossAmount: Long,
    )

    /** 필드 옮겨 담기만. 요율 정책과 배송비 안분액은 서비스가 계산해 넘긴다. */
    fun toEntity(
        item: Item,
        shippingFee: Long,
        feePolicy: SettlementFeePolicy,
    ): Settlement =
        Settlement.createOnPurchaseConfirm(
            feePolicy = feePolicy,
            sellerId = sellerId,
            orderId = orderId,
            orderItemId = item.orderItemId,
            grossAmount = item.grossAmount,
            shippingFee = shippingFee,
            sellerCouponBurden = SELLER_COUPON_BURDEN,
            confirmedOn = confirmedOn,
        )

    private companion object {
        /** 셀러가 부담하는 쿠폰이 아직 없다 — 쿠폰이 들어오면 부담 주체를 정한 뒤 명세에 실어 보낸다. */
        private const val SELLER_COUPON_BURDEN = 0L
    }
}
