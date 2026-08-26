package com.aechak.domain.user.user.repository

import com.aechak.domain.user.user.User

interface UserRepository {
    fun findById(id: Long): User?

    fun save(user: User): User

    /** 닉네임 선점 여부 — 본인(excludeUserId) 제외. 비교는 컬럼 collation(ci) 기준. */
    fun isNicknameTaken(
        nickname: String,
        excludeUserId: Long,
    ): Boolean

    /** 전화 HMAC(blind index)으로 점유 계정 조회 — 점유 이전("1번호 1계정")의 검색 키. */
    fun findByPhoneHmac(phoneHmac: ByteArray): User?

    /** 영속성 컨텍스트 변경분 즉시 반영 — 점유 해제→세팅처럼 UNIQUE 제약과 UPDATE 순서가 얽힌 흐름에서 쓴다. */
    fun flush()

    /** 적립금 잔액 조건부 원자 차감 — 차감 성공 여부가 곧 잔액 검증이다. false면 잔액 부족. */
    fun deductPointBalance(
        userId: Long,
        amount: Long,
    ): Boolean
}
