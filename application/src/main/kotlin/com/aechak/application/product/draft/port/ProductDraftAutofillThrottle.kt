package com.aechak.application.product.draft.port

import java.time.Duration

/**
 * 셀러당 초안 호출 쿨다운. 구현은 infra/redis 다.
 *
 * VerificationCodeStore 를 재사용하지 않는다 — 전화 인증 전용 계약이고 시그니처가 phoneNumber 에
 * 묶여 있다. 다만 구현 모양은 그대로 베낀다: **검사와 설정이 한 명령**이어야 한다. 쪼개면 그
 * 사이로 동시 요청이 통과해 모델이 두 번 불린다(= 돈이 두 번 나간다).
 *
 * 보상(쿨다운 지우기) 연산을 두지 않는다. 전화 인증은 문자가 안 나가면 쿨다운을 지워 줘야
 * 하지만, 초안 실패는 UNAVAILABLE 로 끝나고 쿨다운이 남는 것이 맞다 — AI 가 죽어 있을 때
 * 셀러가 무제한으로 때리게 할 이유가 없다.
 */
interface ProductDraftAutofillThrottle {
    /** 쿨다운이 안 걸려 있을 때만 걸고 성공 여부를 돌려준다(SET NX PX 한 명령) */
    fun tryAcquire(
        sellerId: Long,
        ttl: Duration,
    ): Boolean
}
