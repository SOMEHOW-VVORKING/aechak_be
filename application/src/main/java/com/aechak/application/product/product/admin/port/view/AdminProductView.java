package com.aechak.application.product.product.admin.port.view;

import com.aechak.domain.product.product.enums.InspectionStatus;
import com.aechak.domain.product.product.enums.SaleStatus;
import java.time.LocalDateTime;

/**
 * 어드민 상품 목록의 한 행 — 카탈로그 전수 기준이다.
 *
 * <p>구매자 노출 조건(검수 통과·노출 가능 판매 상태·활성 카테고리·활성 셀러)을 걸지 않는다.
 * 운영자는 숨은 상품까지 세고, 검수와 무관한 이유로 판매를 막는 곳이 이 화면이다.
 *
 * @param productPublicId 모든 쓰기 경로의 경로 변수(ULID)
 * @param storeName 셀러 상호명. sellers는 값 참조라 행이 없으면 null
 * @param adminSaleBlockedAt 운영자가 막은 시각. null이면 막혀 있지 않다
 * @param reportCount 그 상품에 누적된 신고 행 수. 처리 상태로 거르지 않는다
 */
public record AdminProductView(
        long productId,
        String productPublicId,
        String productName,
        long sellerId,
        String storeName,
        String categoryName,
        long regularPrice,
        Long discountPrice,
        SaleStatus saleStatus,
        InspectionStatus inspectionStatus,
        LocalDateTime createdAt,
        LocalDateTime adminSaleBlockedAt,
        String adminSaleBlockReason,
        long reportCount) {}
