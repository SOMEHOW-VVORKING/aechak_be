package com.aechak.application.order.seller.port.view

import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 셀러 주문 한 건의 주문 단위 읽기 모델. 목록 행과 상세가 같은 필드를 쓰고, 상세만 품목을 덧붙인다.
 *
 * - 금액은 전부 이 셀러 몫이다 — 결제 단위(주문그룹) 합계가 아니라 orders 행과 그 품목의 합이다
 * - 수령인명·연락처는 암호문 그대로 담는다. 복호는 application(service) 소관
 * - itemCount·totalProductAmount는 품목 집계를 쿼리에서 끝낸 값이다
 */
data class SellerOrderSummaryView(
    val orderId: Long,
    val orderPublicId: String,
    val orderGroupPublicId: String,
    val status: OrderStatus,
    val orderedAt: LocalDateTime,
    val buyerId: Long,
    val receiverNameEnc: String,
    val contactNumberEnc: String,
    val zipCode: String,
    val baseAddress: String,
    val detailAddress: String?,
    val deliveryMemo: String?,
    val itemCount: Long,
    val totalProductAmount: Long,
    val sellerShippingFee: Long,
    val allocatedCouponDiscount: Long,
    val purchaseConfirmDueAt: LocalDateTime?,
)
