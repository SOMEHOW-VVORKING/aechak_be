package com.aechak.application.seller.usecase.result;

import com.aechak.application.seller.port.view.AdminSellerView;
import com.aechak.domain.seller.application.enums.BusinessType;
import com.aechak.domain.seller.seller.enums.SellerStatus;
import java.time.LocalDateTime;

/**
 * 셀러 목록·상세의 한 셀러.
 *
 * <p>대표자명은 마스킹해서 내보낸다. 목록은 한 요청에 최대 100행의 대표자명을 한꺼번에 내보내는
 * 자리라 평문을 실으면 노출면이 행 수만큼 커지고, 통장사본 대조처럼 평문이 필요한 작업은
 * 이미 입점 심사 상세가 담당한다. 신청서 없이 심은 셀러는 유형·대표자명이 비어 있다.
 *
 * @param suspensionUntil 현재 정지의 해제 예정 시각. 기간이 지나도 저절로 풀리지 않는다 — 해제는 운영자 조치다
 * @param statusChangedAt 운영 상태가 마지막으로 바뀐 시각
 */
public record AdminSellerSummaryResult(
        long sellerId,
        String storeName,
        SellerStatus status,
        BusinessType businessType,
        String representativeNameMasked,
        long productCount,
        int confirmedOrderCount,
        boolean hasUnsettledAmount,
        LocalDateTime statusChangedAt,
        LocalDateTime suspensionUntil) {

    public static AdminSellerSummaryResult from(AdminSellerView view) {
        return new AdminSellerSummaryResult(
                view.sellerId(),
                view.storeName(),
                view.status(),
                view.businessType(),
                maskName(view.representativeName()),
                view.productCount(),
                view.confirmedOrderCount(),
                view.hasUnsettledAmount(),
                view.statusChangedAt(),
                view.suspensionUntil());
    }

    /** 가운데를 가리고 성과 끝 글자만 남긴다 — 한 글자 이름은 가릴 자리가 없어 그대로 둔다. */
    private static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String stripped = name.strip();
        if (stripped.length() == 1) {
            return stripped;
        }
        if (stripped.length() == 2) {
            return stripped.charAt(0) + "*";
        }
        return stripped.charAt(0) + "*".repeat(stripped.length() - 2) + stripped.charAt(stripped.length() - 1);
    }
}
