package com.aechak.application.order.usecase.query

import java.time.LocalDateTime

/**
 * 만료 대상 스캔 조건. 커서는 앞 페이지에서 마지막으로 살펴본 건이고, 처리했는지와는 무관하다 —
 * 결제가 진행 중이라 넘긴 건이 다음 회차까지 앞자리를 독점하지 못하게 하려는 것이다.
 */
data class ExpiredOrderGroupQuery(
    val limit: Int,
    val afterExpiresAt: LocalDateTime? = null,
    val afterOrderGroupId: Long? = null,
) {
    init {
        require(limit > 0) { "스캔 건수는 1 이상이어야 합니다 (limit=$limit)" }
        require((afterExpiresAt == null) == (afterOrderGroupId == null)) {
            "커서는 만료시각과 id가 함께 있어야 합니다 (afterExpiresAt=$afterExpiresAt, afterOrderGroupId=$afterOrderGroupId)"
        }
    }
}
