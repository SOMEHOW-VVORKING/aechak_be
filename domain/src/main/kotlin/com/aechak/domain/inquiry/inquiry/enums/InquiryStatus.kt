package com.aechak.domain.inquiry.inquiry.enums

/** 문의 처리 상태 */
enum class InquiryStatus {
    RECEIVED, // 접수
    IN_PROGRESS, // 처리중
    DONE, // 완료
    ON_HOLD, // 보류
}
