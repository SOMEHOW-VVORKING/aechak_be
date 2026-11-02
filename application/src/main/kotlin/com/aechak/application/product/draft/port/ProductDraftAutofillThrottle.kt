package com.aechak.application.product.draft.port

import java.time.Duration

/**
 * 셀러당 초안 호출 쿨다운. 구현은 infra/redis 다.
 *
 * 검사와 설정이 한 명령이어야 한다 — 쪼개면 그 사이로 동시 요청이 통과해 모델이 두 번 불린다.
 * 보상(쿨다운 지우기)은 두지 않는다. AI 가 죽어 있을 때 무제한 재시도를 허용할 이유가 없다.
 */
interface ProductDraftAutofillThrottle {
    /** 쿨다운이 안 걸려 있을 때만 걸고 성공 여부를 돌려준다(SET NX PX 한 명령) */
    fun tryAcquire(
        sellerId: Long,
        ttl: Duration,
    ): Boolean
}
