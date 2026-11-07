package com.aechak.application.seller.usecase.query;

import com.aechak.application.support.PageQuery;
import com.aechak.domain.seller.seller.enums.SellerStatus;

/**
 * 어드민 셀러 목록 조회 조건.
 *
 * @param status 운영 상태. null이면 전체
 * @param typeFilter 판매자 유형 축
 * @param storeNameKeyword 상호명 부분일치. null이면 전체
 */
public record AdminSellerSearchQuery(
        SellerStatus status, SellerTypeFilter typeFilter, String storeNameKeyword, int page, int size) {

    /**
     * 형식 오류는 보정한다(회원 목록·심사 목록과 같은 결) — page·size는 PageQuery가 절삭하고,
     * 알 수 없는 상태·유형은 조건 없음으로 본다. 목록 필터의 오타로 화면이 비는 대신 전체를 보여 준다.
     */
    public static AdminSellerSearchQuery of(String status, String sellerType, String keyword, int page, int size) {
        PageQuery pageQuery = PageQuery.of(page, size);
        return new AdminSellerSearchQuery(
                parseStatus(status),
                SellerTypeFilter.from(sellerType),
                strippedOrNull(keyword),
                pageQuery.getPage(),
                pageQuery.getSize());
    }

    private static SellerStatus parseStatus(String raw) {
        String stripped = strippedOrNull(raw);
        if (stripped == null) {
            return null;
        }
        for (SellerStatus status : SellerStatus.values()) {
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
