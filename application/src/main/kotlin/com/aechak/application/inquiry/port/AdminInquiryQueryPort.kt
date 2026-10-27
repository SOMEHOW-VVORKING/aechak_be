package com.aechak.application.inquiry.port

import com.aechak.application.inquiry.port.view.AdminInquiryView
import com.aechak.application.inquiry.port.view.PreviousInquiryView
import java.time.LocalDateTime

/** 운영자용 문의 목록·이력 조회 포트 — 작성자로 잠그지 않는 전역 조회다. */
interface AdminInquiryQueryPort {
    fun findPage(condition: AdminInquiryCondition): List<AdminInquiryView>

    /** 필터 조건의 총건수 — 커서 앵커는 적용하지 않는다(첫 페이지에서만 쓴다). */
    fun count(condition: AdminInquiryCondition): Long

    /**
     * 같은 작성자가 기준 문의보다 먼저 접수한 문의 — 최신순. 반복 문의 판단 보조.
     * "먼저"는 (created_at, id) 복합 키로 판정한다 — 접수 시각이 같은 행은 id로 가른다.
     * 기준 행 자신은 이 비교에서 자동으로 빠진다.
     */
    fun findPreviousByUserId(
        userId: Long,
        beforeCreatedAt: LocalDateTime,
        beforeInquiryId: Long,
        limit: Int,
    ): List<PreviousInquiryView>
}
