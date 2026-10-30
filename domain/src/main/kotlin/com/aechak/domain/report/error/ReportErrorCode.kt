package com.aechak.domain.report.error

import com.aechak.common.error.ErrorCode

/** 신고 처리(운영자 결정) 전용 코드 블록. 세 BC 어디의 소유도 아닌 교차 관심사라 따로 둔다. */
enum class ReportErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    REPORT_NOT_FOUND(140000, "신고를 찾을 수 없습니다.", 404),
    REPORT_ALREADY_DECIDED(140001, "이미 처리된 신고입니다.", 409),
    REPORT_DECISION_NOT_APPLICABLE(140002, "이 신고 대상에는 비공개 전환을 적용할 수 없습니다.", 400),
    REPORT_SANCTION_DETAIL_REQUIRED(140003, "계정 제재는 제재 조치와 사유가 함께 필요합니다.", 400),
    REPORT_SANCTION_DETAIL_NOT_ALLOWED(140004, "계정 제재가 아닌 처리에는 제재 내용을 보낼 수 없습니다.", 400),
    INVALID_REPORT_DECISION_REASON(140005, "고른 처리에 맞지 않는 처리 사유입니다.", 400),

    /** 신고 행은 있는데 그 신고가 가리키는 대상이 사라졌다 — 조치할 것이 없어 결정을 남기지 않는다. */
    REPORT_TARGET_NOT_FOUND(140006, "신고 대상을 찾을 수 없습니다.", 404),
}
