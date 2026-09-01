package com.aechak.application.order.usecase

import com.aechak.application.order.usecase.result.ExpireTargetResult

/**
 * 만료 배치 진입점 계약. 루프와 실패 정책은 호출자(배치 잡)가 소유하고 여기는 명단과 한 건 처리만 제공함.
 * 건너뛴 건도 결제대기로 남아 다시 조회되므로 after가 전진하지 않으면 같은 명단을 다시 돌려줌
 */
interface OrderGroupExpireUseCase {
    /** 만료된 결제대기 그룹을 만료가 이른 순으로, after 뒤부터 limit개 돌려줌 */
    fun findExpireTargets(
        after: ExpireTargetResult?,
        limit: Int,
    ): List<ExpireTargetResult>

    fun cancelIfUnpaid(target: ExpireTargetResult)
}
