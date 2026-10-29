package com.aechak.application.user.sanction.usecase.result

import com.aechak.application.user.sanction.port.view.MemberSummaryView
import com.aechak.domain.user.user.enums.UserStatus
import java.time.LocalDateTime

/**
 * 회원 목록 한 행.
 * status 는 유효 상태다 — 기간이 지난 임시 정지는 ACTIVE 로 내려가고, 저장값은 storedStatus 로 함께 준다.
 * 둘을 같이 주는 이유: 운영자가 "실제로는 풀렸지만 7일 정지 이력이 있다"를 화면에서 구분할 수 있어야 한다.
 */
data class MemberSummaryResult(
    val userId: Long,
    val publicId: String,
    val nickname: String?,
    val status: UserStatus,
    val storedStatus: UserStatus,
    val sanctionUntil: LocalDateTime?,
    val warningCount: Int,
    val reportCount: Long,
    val joinedAt: LocalDateTime,
) {
    companion object {
        fun from(
            view: MemberSummaryView,
            at: LocalDateTime,
        ): MemberSummaryResult =
            MemberSummaryResult(
                userId = view.userId,
                publicId = view.publicId,
                nickname = view.nickname,
                status = UserStatus.effective(view.status, view.sanctionUntil, at),
                storedStatus = view.status,
                sanctionUntil = view.sanctionUntil,
                warningCount = view.warningCount,
                reportCount = view.reportCount,
                joinedAt = view.joinedAt,
            )
    }
}
