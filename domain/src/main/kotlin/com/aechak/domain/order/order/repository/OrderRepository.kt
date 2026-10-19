package com.aechak.domain.order.order.repository

import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.enums.OrderStatus

interface OrderRepository {
    /** 주문품목은 Order의 cascade로 함께 저장된다. */
    fun saveAll(orders: List<Order>): List<Order>

    /** 식별자 단건 조회 — 소유자 없는 호출자(배치)가 쓴다. */
    fun findById(id: Long): Order?

    /** 주문 id 오름차순 */
    fun findAllByOrderGroupId(orderGroupId: Long): List<Order>

    /**
     * 여러 주문그룹의 주문을 품목까지 한 번에 읽는다. 클레임 환불액 재계산은 적립금 안분 분모로
     * 같은 그룹의 주문 전체를 요구하므로, 목록 한 페이지분을 주문마다 질의하지 않게 묶는다.
     */
    fun findAllByOrderGroupIdIn(orderGroupIds: Collection<Long>): List<Order>

    /** 구매자 스코프 단건 조회 — 타인 주문은 없는 것으로 본다. */
    fun findByPublicIdAndBuyerId(
        publicId: String,
        buyerId: Long,
    ): Order?

    /**
     * 주문 행 선점 조회. 같은 주문에 겹친 클레임 요청을 직렬화한다 — 선점 없이 중복 검사를 하면
     * 두 요청이 모두 "진행 중 없음"을 읽는다. 소유권은 호출측이 확인한다.
     */
    fun findByPublicIdForUpdate(publicId: String): Order?

    /** 셀러 스코프 단건 조회 — 타 셀러 주문은 없는 것으로 본다. */
    fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Order?

    /** 그룹 확정에 따른 셀러 주문 일괄 전이 — 결제대기 행만. 전이된 행 수 반환 */
    fun markAllPaidByOrderGroupId(orderGroupId: Long): Int

    /** 기대 상태인 행만 전이한다 — 동시 전이의 선점 심판. 전이됐으면 true */
    fun transitionStatusIf(
        id: Long,
        expected: OrderStatus,
        next: OrderStatus,
    ): Boolean
}
