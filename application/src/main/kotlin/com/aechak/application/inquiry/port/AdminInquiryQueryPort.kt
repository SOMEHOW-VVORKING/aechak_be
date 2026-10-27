package com.aechak.application.inquiry.port

import com.aechak.application.inquiry.port.view.AdminInquiryView
import com.aechak.application.inquiry.port.view.PreviousInquiryView

/** 운영자용 문의 목록·이력 조회 포트 — 작성자로 잠그지 않는 전역 조회다. */
interface AdminInquiryQueryPort {
    fun findPage(condition: AdminInquiryCondition): List<AdminInquiryView>

    /** 필터 조건의 총건수 — 커서 앵커는 적용하지 않는다(첫 페이지에서만 쓴다). */
    fun count(condition: AdminInquiryCondition): Long

    /** 같은 작성자의 과거 문의 — 자기 행은 빼고 최신순. 반복 문의 판단 보조. */
    fun findPreviousByUserId(
        userId: Long,
        excludedInquiryId: Long,
        limit: Int,
    ): List<PreviousInquiryView>
}
