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
}
