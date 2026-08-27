package com.aechak.application.order.usecase

import com.aechak.application.order.usecase.command.CreateOrderGroupCommand
import com.aechak.application.order.usecase.result.ConfirmGroupPaidResult
import com.aechak.application.order.usecase.result.CreateOrderGroupResult

/**
 * order 도메인의 유일한 진입점 계약. 규칙은 user 도메인 템플릿(UserUseCase) 참조.
 * 입출력 어휘(command/·result/·query/)는 이 패키지 하위에 둔다 — 계약은 usecase/, 구현은 service/.
 */
interface OrderUseCase {
    /** 같은 멱등키의 재요청이면 새로 만들지 않고 최초 생성 결과를 돌려준다. */
    fun createOrderGroup(command: CreateOrderGroupCommand): CreateOrderGroupResult

    /**
     * 결제 확정에 따른 주문 전이 — 그룹 선점(결제대기→결제완료 조건부)과 셀러 주문 일괄 전이.
     * 호출 트랜잭션에 참여한다(REQUIRED) — 결제 기록과 주문 전이가 한 트랜잭션이어야 하는 확정 흐름 전제.
     */
    fun confirmGroupPaid(orderGroupId: Long): ConfirmGroupPaidResult

    /**
     * 확정 커밋 뒤 주문한 항목을 장바구니에서 걷어낸다 — 자기 트랜잭션.
     * 실패는 구매자가 직접 지우면 되는 불편이라 호출부가 삼키고 로그만 남긴다.
     */
    fun clearOrderedCartItems(
        buyerId: Long,
        orderGroupId: Long,
    ): Int
}
