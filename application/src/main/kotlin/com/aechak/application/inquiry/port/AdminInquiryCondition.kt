package com.aechak.application.inquiry.port

import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus
import com.aechak.domain.inquiry.inquiry.enums.InquiryType
import java.time.LocalDateTime

/**
 * 운영자용 문의 목록 조회 조건.
 *
 * - status·inquiryType·keyword가 null이면 해당 조건 미적용
 * - keyword는 문의 내용과 답변 수신 이메일을 함께 훑는다 — 문의에 제목이 없어 내용이 검색 대상이다
 * - anchor가 있으면 그 지점 다음부터 — 정렬은 접수 오래된 순(created_at, id) 고정
 */
data class AdminInquiryCondition(
    val status: InquiryStatus?,
    val inquiryType: InquiryType?,
    val keyword: String?,
    val anchor: AdminInquiryAnchor?,
    val limit: Int,
) {
    init {
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}

/** 커서가 가리키는 마지막 행. created_at만으로는 동시각 행이 갈리지 않아 id를 함께 든다. */
data class AdminInquiryAnchor(
    val lastCreatedAt: LocalDateTime,
    val lastInquiryId: Long,
)
