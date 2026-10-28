package com.aechak.application.settlement.port.view

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 주문 하나로 접은 정산 읽기 모델. 금액은 전부 SQL이 합산한 값이고 다시 계산하지 않는다 —
 * 정산 행에 박힌 값과 화면 숫자가 갈리면 어느 쪽이 지급액인지 알 수 없게 된다.
 *
 * - [confirmedAt]: 확정 없이는 정산 행이 생기지 않아 사실상 항상 채워지지만, 주문 컬럼이 nullable이라 타입만 열어 뒀다
 * - [settlementMonth]·[expectedPayoutDate]·요율 두 개는 한 주문의 정산 행이 모두 같은 값을 갖는다(한 트랜잭션에서
 *   같은 확정일·같은 정책으로 만들어진다). 어긋난 행이 섞이면 가장 큰 값이 선택된다
 * - [statusRank]: [com.aechak.application.settlement.port.SellerSettlementStatus.rank] 의 주문 단위 최댓값
 */
data class SellerSettlementOrderView(
    val sellerId: Long,
    val orderId: Long,
    val orderPublicId: String,
    val settlementMonth: String,
    val confirmedAt: LocalDateTime?,
    val expectedPayoutDate: LocalDate,
    val productAmount: Long,
    val shippingFee: Long,
    val commission: Long,
    val pgFee: Long,
    val sellerCouponBurden: Long,
    val refundDeduction: Long,
    val netAmount: Long,
    val platformFeeRate: BigDecimal,
    val pgFeeRate: BigDecimal,
    val statusRank: Int,
)
