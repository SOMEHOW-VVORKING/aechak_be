package com.aechak.application.settlement.port.view

/**
 * 정산 행 하나(주문품목 하나)의 읽기 모델.
 *
 * [productName]·[quantity]는 주문품목·상품 버전 스냅샷에서 가져오고 없으면 null이다 — 표시용 값 때문에
 * 금액 행을 떨어뜨리지 않기 위해 외부 조인으로 읽는다. 내부 조인이면 스냅샷 행이 사라진 품목 하나가
 * 통째로 빠져 내역의 합이 요약과 어긋난다.
 */
data class SellerSettlementItemView(
    val orderItemId: Long,
    val productName: String?,
    val quantity: Int?,
    val grossAmount: Long,
    val shippingFee: Long,
    val commission: Long,
    val pgFee: Long,
    val sellerCouponBurden: Long,
    val refundDeduction: Long,
    val netAmount: Long,
)
