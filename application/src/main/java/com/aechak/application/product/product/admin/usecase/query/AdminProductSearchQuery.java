package com.aechak.application.product.product.admin.usecase.query;

import com.aechak.application.support.PageQuery;
import com.aechak.domain.product.product.enums.InspectionStatus;
import com.aechak.domain.product.product.enums.SaleStatus;

/**
 * 어드민 상품 목록 조회 조건.
 *
 * @param inspectionStatus 검수 상태. null이면 전체
 * @param saleStatus 판매 상태. null이면 전체
 * @param categoryId 카테고리(하위 포함). null이면 전체
 * @param keyword 상품명 또는 셀러 상호명 부분일치. null이면 전체
 */
public record AdminProductSearchQuery(
        InspectionStatus inspectionStatus, SaleStatus saleStatus, Long categoryId, String keyword, int page, int size) {

    /**
     * 형식 오류는 보정한다(셀러 목록·회원 목록과 같은 결) — page·size는 PageQuery가 절삭하고,
     * 알 수 없는 상태 문자열은 조건 없음으로 본다. 필터 오타로 화면이 비는 대신 전체를 보여 준다.
     */
    public static AdminProductSearchQuery of(
            String inspectionStatus, String saleStatus, Long categoryId, String keyword, int page, int size) {
        PageQuery pageQuery = PageQuery.of(page, size);
        return new AdminProductSearchQuery(
                parseInspectionStatus(inspectionStatus),
                parseSaleStatus(saleStatus),
                categoryId,
                strippedOrNull(keyword),
                pageQuery.getPage(),
                pageQuery.getSize());
    }

    private static InspectionStatus parseInspectionStatus(String raw) {
        String stripped = strippedOrNull(raw);
        if (stripped == null) {
            return null;
        }
        for (InspectionStatus status : InspectionStatus.values()) {
            if (status.name().equalsIgnoreCase(stripped)) {
                return status;
            }
        }
        return null;
    }

    private static SaleStatus parseSaleStatus(String raw) {
        String stripped = strippedOrNull(raw);
        if (stripped == null) {
            return null;
        }
        for (SaleStatus status : SaleStatus.values()) {
            if (status.name().equalsIgnoreCase(stripped)) {
                return status;
            }
        }
        return null;
    }

    private static String strippedOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}
