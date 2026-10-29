package com.aechak.domain.order.order.repository

import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

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

    /**
     * 주문 행 선점 조회(내부 식별자). 클레임을 이미 손에 든 호출자가 쓴다 — 운영자 개입이
     * [findByPublicIdForUpdate]와 **같은 행**을 잠가야 신규 클레임 요청과 직렬화된다.
     * 공개 식별자를 거치려면 주문을 한 번 더 읽어야 하므로 id로 받는다.
     */
    fun findByIdForUpdate(id: Long): Order?

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

    /**
     * 구매확정 기한이 지난 배송완료 주문 id — 진행 중 클레임이 있는 주문은 제외한다.
     * [afterId] 다음 id부터 오름차순 최대 [limit] 건.
     */
    fun findAutoConfirmDueIds(
        dueAtOrBefore: LocalDateTime,
        afterId: Long,
        limit: Int,
    ): List<Long>

    /**
     * 구매확정 사전 안내 시각 기록 — 배송완료이고 아직 안내하지 않은 행만. 기록했으면 true.
     * 안내 컬럼만 건드리는 조건부 UPDATE다 — 엔티티 갱신으로 하면 확정 컬럼까지 되돌릴 수 있다.
     */
    fun markConfirmNotifiedIf(
        id: Long,
        notifiedAt: LocalDateTime,
    ): Boolean

    /**
     * 구매확정 사전 안내 대상 주문 id — 기한이 [dueAtAfter] 초과 [dueAtOrBefore] 이하이고 아직 안내하지 않은 배송완료 주문.
     * [afterId] 다음 id부터 오름차순 최대 [limit] 건.
     */
    fun findConfirmNotifyTargetIds(
        dueAtAfter: LocalDateTime,
        dueAtOrBefore: LocalDateTime,
        afterId: Long,
        limit: Int,
    ): List<Long>
}
