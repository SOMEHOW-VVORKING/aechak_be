package com.aechak.application.seller.port.view;

import com.aechak.domain.seller.application.enums.BusinessType;
import com.aechak.domain.seller.seller.enums.SellerStatus;
import java.time.LocalDateTime;

/**
 * 어드민 셀러 목록·상세의 한 행.
 *
 * <p>사업자 유형과 대표자명은 sellers에 없어 입점 신청서를 조인해 얻는다 — 신청서는 유저당 1행을
 * 재사용하는 모델이고 기셀러는 재신청이 막혀 있어(10001), 셀러의 신청서 행은 곧 승인된 그 행이다.
 * 신청서 없이 심은 셀러는 둘이 비어 있다.
 *
 * <p>representativeName은 평문이다 — 마스킹은 이 값을 밖으로 내보내는 Result가 한다.
 *
 * @param productCount 그 셀러의 등록 상품 수. 판매 상태로 거르지 않는다(운영자는 숨은 상품까지 센다)
 * @param hasUnsettledAmount 지급 대기(PENDING) 정산 원장이 한 행이라도 있는지
 */
public record AdminSellerView(
        long sellerId,
        String storeName,
        SellerStatus status,
        LocalDateTime statusChangedAt,
        LocalDateTime suspensionUntil,
        String suspensionReason,
        String adminMemo,
        int confirmedOrderCount,
        BusinessType businessType,
        String representativeName,
        long productCount,
        boolean hasUnsettledAmount) {}
