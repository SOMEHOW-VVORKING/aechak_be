package com.aechak.domain.user.user.repository

import com.aechak.domain.user.user.User

interface UserRepository {
    fun findById(id: Long): User?

    fun save(user: User): User

    /** 적립금 잔액 캐시를 원자 증감한다. */
    fun addPointBalance(
        userId: Long,
        amount: Long,
    ): Int

    /** 닉네임 선점 여부 — 본인(excludeUserId) 제외. 비교는 컬럼 collation(ci) 기준. */
    fun isNicknameTaken(
        nickname: String,
        excludeUserId: Long,
    ): Boolean

    /** 전화 HMAC(blind index)으로 점유 계정 조회 — 점유 이전("1번호 1계정")의 검색 키. */
    fun findByPhoneHmac(phoneHmac: ByteArray): User?

    /** 영속성 컨텍스트 변경분 즉시 반영 — 점유 해제→세팅처럼 UNIQUE 제약과 UPDATE 순서가 얽힌 흐름에서 쓴다. */
    fun flush()
}
