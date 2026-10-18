package com.aechak.application.order.usecase

import com.aechak.application.order.usecase.command.ExpireOrderGroupCommand
import com.aechak.application.order.usecase.result.ExpireOrderGroupResult
import com.aechak.application.order.usecase.result.ExpiredOrderGroupResult

/**
 * 주문서 TTL 만료 계약 — 배치만 소비한다. 구매자 앱이 쓰는 OrderUseCase와 메서드가 겹치지 않아 인터페이스를 갈랐고,
 * 구현은 OrderFacade 하나다.
 *
 * 스캔과 확정을 나눈 이유는 확정 전에 결제 결과를 먼저 가려내야 하기 때문이다 —
 * 그 조회는 외부 호출이라 트랜잭션 밖(배치)에서 일어나야 한다.
 */
interface OrderExpiryUseCase {
    /** 만료 대상 스캔 — 결제대기이면서 만료 시각이 지난 주문그룹을 limit건까지 */
    fun findExpiredOrderGroups(limit: Int): List<ExpiredOrderGroupResult>

    /**
     * 만료 확정 — 결제대기→취소 조건부 전이에 성공한 경우에만 재고와 적립금을 되돌린다.
     * 재고 복원에는 멱등키가 없으므로 그 전이 성공이 복원의 유일한 게이트다. 두 번 돌려도 자산은 한 번만 늘어난다.
     */
    fun expireOrderGroup(command: ExpireOrderGroupCommand): ExpireOrderGroupResult
}
