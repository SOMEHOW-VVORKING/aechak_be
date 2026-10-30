package com.aechak.application.user.sanction.usecase.query

import com.aechak.application.support.CursorPageSize
import java.time.LocalDate

/**
 * 회원 목록 조회 조건. keyword 는 닉네임 부분일치 또는 회원 id 완전일치로 쓴다 —
 * 운영자가 목록의 "회원 ID / 닉네임" 한 칸에 둘 중 무엇을 넣어도 찾히게 한다.
 */
data class MemberSearchQuery(
    val statusFilter: MemberStatusFilter,
    val keyword: String?,
    val joinedFrom: LocalDate?,
    val joinedTo: LocalDate?,
    val cursor: String?,
    val size: Int,
) {
    init {
        require(size >= 1) { "size는 1 이상이어야 합니다." }
    }

    companion object {
        /** 형식 오류는 보정한다(관리자 명부 목록과 같은 결) — size 절삭, 알 수 없는 상태는 ALL. */
        @JvmStatic
        fun of(
            status: String?,
            keyword: String?,
            joinedFrom: LocalDate?,
            joinedTo: LocalDate?,
            cursor: String?,
            size: Int,
        ): MemberSearchQuery =
            MemberSearchQuery(
                statusFilter = MemberStatusFilter.from(status),
                keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
                joinedFrom = joinedFrom,
                joinedTo = joinedTo,
                cursor = cursor?.trim()?.takeIf { it.isNotEmpty() },
                size = size.coerceIn(CursorPageSize.MIN.toInt(), CursorPageSize.MAX.toInt()),
            )
    }
}
