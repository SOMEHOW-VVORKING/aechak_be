package com.aechak.application.user.sanction.port

import com.aechak.application.user.sanction.port.view.MemberSummaryView
import com.aechak.application.user.sanction.port.view.SanctionHistoryView
import com.aechak.domain.user.user.enums.UserStatus
import java.time.LocalDateTime

/**
 * 어드민 회원 조회 포트. 애그리거트 계약이 아니라 운영 화면 전용 읽기 모델이라
 * domain 리포지토리가 아니라 소비자(application)가 정의한다.
 */
interface AdminMemberQueryPort {
    /** 가입 역순(id 내림차순) 한 페이지. role=ADMIN 계정은 관리자 명부가 따로 있어 제외한다. */
    fun findMemberPage(condition: MemberPageCondition): List<MemberSummaryView>

    /** 같은 조건의 전체 행 수 — 운영자가 "조건에 맞는 회원 N명"을 봐야 한다. */
    fun countMembers(condition: MemberPageCondition): Long

    fun findMember(userId: Long): MemberSummaryView?

    fun findSanctionHistory(
        userId: Long,
        limit: Int,
    ): List<SanctionHistoryView>
}

/**
 * 목록 조회 조건의 영속 계층 어휘. statuses 가 비면 상태 조건 없음,
 * joinedFrom/joinedTo 는 반열림 구간[from, to)으로 해석한다 — 날짜 경계의 하루치가 새지 않게.
 */
data class MemberPageCondition(
    val statuses: List<UserStatus>,
    val nicknameKeyword: String?,
    val userIdKeyword: Long?,
    val joinedFrom: LocalDateTime?,
    val joinedToExclusive: LocalDateTime?,
    val lastUserId: Long?,
    val limit: Int,
)
