package com.aechak.application.order.usecase

import com.aechak.application.order.usecase.command.AutoConfirmPurchaseCommand
import com.aechak.application.order.usecase.command.ConfirmPurchaseCommand
import com.aechak.application.order.usecase.command.CreateOrderGroupCommand
import com.aechak.application.order.usecase.command.MarkConfirmNotifiedCommand
import com.aechak.application.order.usecase.query.AutoConfirmScanQuery
import com.aechak.application.order.usecase.query.ConfirmNotifyScanQuery
import com.aechak.application.order.usecase.result.ConfirmGroupPaidResult
import com.aechak.application.order.usecase.result.CreateOrderGroupResult
import com.aechak.application.order.usecase.result.OrderItemForReviewResult

/**
 * order 도메인의 유일한 진입점 계약. 규칙은 user 도메인 템플릿(UserUseCase) 참조.
 * 입출력 어휘(command/·result/·query/)는 이 패키지 하위에 둔다 — 계약은 usecase/, 구현은 service/.
 */
interface OrderUseCase {
    /** 같은 멱등키의 재요청이면 새로 만들지 않고 최초 생성 결과를 돌려준다. */
    fun createOrderGroup(command: CreateOrderGroupCommand): CreateOrderGroupResult

    /** 리뷰 작성 자격 판정용 본인 주문품목 조회. 없거나 본인 주문이 아니면 null. */
    fun getOrderItemForReview(
        orderItemId: Long,
        buyerId: Long,
    ): OrderItemForReviewResult?

    /** 만료 전 결제대기 주문그룹 수 */
    fun countPendingPaymentOrderGroups(buyerId: Long): Long

    /**
     * 결제 확정에 따른 주문 전이 — 그룹 선점(결제대기→결제완료 조건부)과 셀러 주문 일괄 전이.
     * 호출 트랜잭션에 참여한다(REQUIRED) — 결제 기록과 주문 전이가 한 트랜잭션이어야 하는 확정 흐름 전제.
     */
    fun confirmGroupPaid(orderGroupId: Long): ConfirmGroupPaidResult

    /**
     * 구매자 구매확정 — 배송완료 건만, 진행 중 클레임이 없을 때. 확정과 적립금 지급이 한 트랜잭션이다.
     * 재호출은 50008로 거절되고 적립금은 다시 지급되지 않는다.
     */
    fun confirmPurchase(command: ConfirmPurchaseCommand)

    /** 자동확정 대상 주문 id — 기한이 지난 배송완료 건 중 진행 중 클레임이 없는 것만. 커서 오름차순. */
    fun findAutoConfirmTargetIds(query: AutoConfirmScanQuery): List<Long>

    /**
     * 기한이 지난 건의 자동확정 — 수동 확정과 같은 전이·적립 경로를 탄다(적립 멱등키도 같다).
     * 기한 미도래·확정 불가 상태·진행 중 클레임이면 BusinessException으로 거절하고 전이 선점도 함께 되돌린다.
     */
    fun autoConfirmPurchase(command: AutoConfirmPurchaseCommand)

    /** 사전 안내 대상 주문 id — 안내 구간 안이고 아직 안내하지 않은 건. 커서 오름차순. */
    fun findConfirmNotifyTargetIds(query: ConfirmNotifyScanQuery): List<Long>

    /**
     * 구매확정 사전 안내 기록 — 알림 발송 경로가 붙기 전까지 안내 시각만 남긴다. 기록했으면 true.
     * 이미 안내한 건과 이미 확정된 건은 조건부 UPDATE가 걸러 false를 돌려준다.
     */
    fun markConfirmNotified(command: MarkConfirmNotifiedCommand): Boolean

    /**
     * 확정 커밋 뒤 주문한 항목을 장바구니에서 걷어낸다 — 자기 트랜잭션.
     * 실패는 구매자가 직접 지우면 되는 불편이라 호출부가 삼키고 로그만 남긴다.
     */
    fun clearOrderedCartItems(
        buyerId: Long,
        orderGroupId: Long,
    ): Int
}
