package com.aechak.application.inquiry.usecase.query;

import com.aechak.application.support.CursorPageSize;
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;
import com.aechak.domain.inquiry.inquiry.enums.InquiryType;

/**
 * 운영자용 문의 목록 조회 입력. of()가 형식 오류를 보정한다 — size는 1~100 절삭, 공백뿐인 keyword는 미적용(null).
 * PageQuery.of의 보정 관례와 같은 결이고, 커서는 그대로 넘겨 코덱이 판정한다(형식 오류는 90002).
 */
public record AdminInquirySearchQuery(
        InquiryStatus status, InquiryType inquiryType, String keyword, String cursor, int size) {

    public static AdminInquirySearchQuery of(
            InquiryStatus status, InquiryType inquiryType, String keyword, String cursor, int size) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        int normalizedSize = (int) Math.min(Math.max(size, CursorPageSize.MIN), CursorPageSize.MAX);
        return new AdminInquirySearchQuery(status, inquiryType, normalizedKeyword, cursor, normalizedSize);
    }
}
