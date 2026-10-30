package com.aechak.application.user.sanction.port.view

import com.aechak.domain.user.user.enums.UserStatus
import java.time.LocalDateTime

/**
 * 회원 목록 한 행의 읽기 모델. status 는 저장값이고, 만료된 임시 정지 판정은 application 이 붙인다 —
 * 조회 시점 판정의 주인은 도메인 규칙 한 곳이고 읽기 포트는 행만 떠 온다.
 */
data class MemberSummaryView(
    val userId: Long,
    val publicId: String,
    val nickname: String?,
    val status: UserStatus,
    val sanctionUntil: LocalDateTime?,
    val warningCount: Int,
    val reportCount: Long,
    val joinedAt: LocalDateTime,
)
