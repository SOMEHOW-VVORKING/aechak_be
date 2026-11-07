package com.aechak.admin.seller.response;

import com.aechak.application.seller.usecase.result.AdminSellerSummaryResult;
import com.aechak.domain.seller.application.enums.BusinessType;
import com.aechak.domain.seller.seller.enums.SellerStatus;
import java.time.LocalDateTime;

/**
 * 셀러 목록·상세의 한 셀러.
 *
 * <p>대표자명은 서버가 가려서 내려준다(가운데를 가리고 성과 끝 글자만 남긴다). 입점 신청서가 없는
 * 셀러는 businessType·representativeNameMasked가 null이다.
 *
 * <p>suspensionUntil은 현재 정지의 해제 예정 시각이다 — 그 시각이 지나도 상태는 저절로 풀리지 않는다.
 * 무기한 정지와 정지 아닌 셀러는 null이다.
 *
 * <p>화면 목업의 "최근 접속"과 "신고 누적"은 싣지 않는다 — users에 최근 접속 칼럼이 없고,
 * 신고 대상 종류에 셀러가 없어 셀 수 있는 값이 아니다.
 */
public record AdminSellerSummaryResponse(
        long sellerId,
        String storeName,
        SellerStatus status,
        BusinessType businessType,
        String representativeNameMasked,
        long productCount,
        int cumulativeOrderCount,
        boolean hasUnsettledAmount,
        LocalDateTime statusChangedAt,
        LocalDateTime suspensionUntil) {

    public static AdminSellerSummaryResponse from(AdminSellerSummaryResult result) {
        return new AdminSellerSummaryResponse(
                result.sellerId(),
                result.storeName(),
                result.status(),
                result.businessType(),
                result.representativeNameMasked(),
                result.productCount(),
                result.confirmedOrderCount(),
                result.hasUnsettledAmount(),
                result.statusChangedAt(),
                result.suspensionUntil());
    }
}
