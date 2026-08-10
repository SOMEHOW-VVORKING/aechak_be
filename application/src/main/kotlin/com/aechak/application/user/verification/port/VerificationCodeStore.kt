package com.aechak.application.user.verification.port

import java.time.Duration
import java.time.Instant

/** 발급된 인증 코드 — 발송 번호와 한 몸으로 저장한다(발송 후 번호 바꿔치기 차단). */
data class IssuedVerificationCode(
    val phoneNumber: String,
    val code: String,
)

/** 일 발송 카운트 — 유저·번호 이중 상한(한쪽만으론 번호 갈아타기/계정 갈아타기를 못 막는다). */
data class DailySendCounts(
    val userCount: Long,
    val phoneCount: Long,
)

/**
 * 인증 코드·발송 제한 저장소 포트 — 세션류 단명 데이터(코드·쿨다운·카운터)를 담당한다.
 * 카운터 증가는 원자 연산이어야 한다(GET-then-SET 금지) — 상한 판정은 증가 후 반환값으로 호출자가 한다.
 */
interface VerificationCodeStore {
    /** 코드 저장 — 재발송이면 교체(이전 코드 즉시 무효)하고 confirm 시도 카운터도 함께 초기화한다. */
    fun saveCode(
        userId: Long,
        phoneNumber: String,
        code: String,
        ttl: Duration,
    )

    fun findCode(userId: Long): IssuedVerificationCode?

    /** 코드 소각 — 인증 성공·시도 초과·발송 실패 보상이 공용으로 쓴다. 시도 카운터도 함께 지운다. */
    fun removeCode(userId: Long)

    /**
     * 재발송 쿨다운 선점 — 걸려 있지 않을 때만 걸고 성공 여부를 돌려준다(SET NX EX 한 방).
     * 검사와 설정을 쪼개면 그 사이 벤더 호출 동안 동시 요청이 통과해 문자가 두 통 나가고 코드가 덮어써진다.
     */
    fun tryStartCooldown(
        userId: Long,
        ttl: Duration,
    ): Boolean

    /** 선점 보상 — 결국 문자를 못 보냈으면 쿨다운도 남기지 않는다. */
    fun clearCooldown(userId: Long)

    /** 일 카운터 원자 증가 — dateKey 버킷별로 유저·번호 둘 다 올리고 증가 후 값을 돌려준다. */
    fun incrementDailyCounts(
        userId: Long,
        phoneNumber: String,
        dateKey: String,
        expireAt: Instant,
    ): DailySendCounts

    /** 발송 실패 보상 — 실패한 시도는 일 상한을 소모하지 않는다. */
    fun rollbackDailyCounts(
        userId: Long,
        phoneNumber: String,
        dateKey: String,
    )

    /** confirm 시도 원자 증가 — 증가 후 값을 돌려준다(초과 판정·무효화는 호출자 몫). */
    fun incrementAttempts(
        userId: Long,
        ttl: Duration,
    ): Long
}
