package com.aechak.domain.inquiry.error

import com.aechak.common.error.ErrorCode

/** 문의 도메인 에러 코드 */
enum class InquiryErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    // 앱 서비스 문의(Inquiry)
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

    // 상품 문의(ProductInquiry) — 1200xx와 구간을 가른다. 두 문의는 보내는 사람도 받는 사람도
    // 다르므로(서비스 문의는 고객이 운영자에게, 상품 문의는 구매자가 셀러에게) 코드가 섞이면
    // 응답만 보고 어느 쪽 문의인지 가릴 수 없다.
    PRODUCT_INQUIRY_NOT_FOUND(120100, "상품 문의를 찾을 수 없습니다.", 404),
    PRODUCT_INQUIRY_TITLE_REQUIRED(120101, "문의 제목은 필수입니다.", 400),
    PRODUCT_INQUIRY_TITLE_TOO_LONG(120102, "문의 제목은 100자를 넘을 수 없습니다.", 400),
    PRODUCT_INQUIRY_CONTENT_REQUIRED(120103, "문의 내용은 필수입니다.", 400),
    PRODUCT_INQUIRY_CONTENT_TOO_LONG(120104, "문의 내용은 1000자를 넘을 수 없습니다.", 400),
    PRODUCT_INQUIRY_ANSWER_REQUIRED(120105, "답변 내용은 필수입니다.", 400),
    PRODUCT_INQUIRY_ANSWER_TOO_LONG(120106, "답변 내용은 2000자를 넘을 수 없습니다.", 400),
    PRODUCT_INQUIRY_ACCESS_DENIED(120107, "본인이 등록한 문의만 철회할 수 있습니다.", 403),
    PRODUCT_INQUIRY_ANSWER_FORBIDDEN(120108, "자기 상품에 온 문의만 답변할 수 있습니다.", 403),
    PRODUCT_INQUIRY_ALREADY_ANSWERED(120109, "이미 답변이 등록된 문의입니다.", 409),
    PRODUCT_INQUIRY_ALREADY_WITHDRAWN(120110, "이미 철회한 문의입니다.", 409),
    PRODUCT_INQUIRY_CANNOT_WITHDRAW_ANSWERED(120111, "답변이 등록된 문의는 철회할 수 없습니다.", 409),
    PRODUCT_INQUIRY_SELLER_NOT_ACTIVE(120112, "활성 셀러만 문의를 읽고 답변할 수 있습니다.", 403),
    PRODUCT_INQUIRY_SELF_ASK_FORBIDDEN(120113, "자기 상품에는 문의를 등록할 수 없습니다.", 400),
    PRODUCT_INQUIRY_WITHDRAWN(120114, "철회된 문의에는 답변할 수 없습니다.", 409),
}
