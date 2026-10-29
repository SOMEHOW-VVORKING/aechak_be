package com.aechak.domain.inquiry.error

import com.aechak.common.error.ErrorCode

/** 문의 도메인 에러 코드 */
enum class InquiryErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    INQUIRY_CONTENT_REQUIRED(120000, "문의 내용은 필수입니다.", 400),
    INQUIRY_CONTENT_TOO_LONG(120001, "문의 내용은 2000자를 넘을 수 없습니다.", 400),
    INQUIRY_REPLY_EMAIL_REQUIRED(120002, "답변받을 이메일은 필수입니다.", 400),
    INQUIRY_REPLY_EMAIL_TOO_LONG(120003, "답변받을 이메일은 255자를 넘을 수 없습니다.", 400),
    INQUIRY_INVALID_REPLY_EMAIL(120004, "답변받을 이메일 형식이 올바르지 않습니다.", 400),
    INQUIRY_NOT_FOUND(120005, "문의를 찾을 수 없습니다.", 404),
    INQUIRY_ANSWER_REQUIRED(120006, "답변 내용은 필수입니다.", 400),
    INQUIRY_ANSWER_TOO_LONG(120007, "답변 내용은 1000자를 넘을 수 없습니다.", 400),
    INQUIRY_ALREADY_ANSWERED(120008, "이미 답변이 등록된 문의입니다.", 409),
    INQUIRY_STATUS_TRANSITION_NOT_ALLOWED(120009, "현재 상태에서 요청한 상태로 변경할 수 없습니다.", 409),
}
