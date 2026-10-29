package com.aechak.domain.user.sanction.repository

import com.aechak.domain.user.sanction.UserSanction

interface UserSanctionRepository {
    fun save(sanction: UserSanction): UserSanction

    /** 대상 회원의 제재 이력 — 최신순(id 내림차순). */
    fun findRecentByTargetUserId(
        targetUserId: Long,
        limit: Int,
    ): List<UserSanction>

    fun countByTargetUserId(targetUserId: Long): Long
}
