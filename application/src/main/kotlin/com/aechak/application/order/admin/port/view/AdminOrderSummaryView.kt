package com.aechak.application.order.admin.port.view

import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 어드민이 보는 주문 한 건의 주문 단위 읽기 모델. 목록 행과 상세가 같은 필드를 쓴다.
 *
 * - 셀러 스코프가 없다 — 운영자는 전체 주문을 본다. 결제대기 주문은 호출자가 상태 집합에서 뺀다
 * - 셀러 이름은 `orders.seller_name_snapshot`(주문 시점 상호)이다. seller BC를 조인하지 않는다 —
 *   셀러 퇴점·개명 뒤에도 그 주문을 처리했던 상호가 남아야 운영 이력이 읽힌다
 * - 수령인명·연락처·주소는 담지 않는다. 배송 진행 감시에 쓰이지 않는 개인정보다
 * - [orderedAt]은 `orders.created_at`이다. 커서·정렬·지연 기한 판정을 한 테이블에서 끝낸다
 */
data class AdminOrderSummaryView(
    val orderId: Long,
    val orderPublicId: String,
    val orderGroupPublicId: String,
    val status: OrderStatus,
    val orderedAt: LocalDateTime,
    val sellerId: Long,
    val sellerName: String?,
    val buyerId: Long,
    val sellerShippingFee: Long,
    val allocatedCouponDiscount: Long,
    val purchaseConfirmDueAt: LocalDateTime?,
    val purchaseConfirmedAt: LocalDateTime?,
    val confirmType: ConfirmType?,
)
