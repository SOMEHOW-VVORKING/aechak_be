package com.aechak.admin.product.response;

import com.aechak.application.product.product.admin.usecase.result.AdminProductSummaryResult;
import com.aechak.domain.product.product.enums.InspectionStatus;
import com.aechak.domain.product.product.enums.SaleStatus;
import java.time.LocalDateTime;

/**
 * 상품 목록의 한 행.
 *
 * <p>inspectionStatus가 화면의 "검수 통과/블라인드"를 가르는 값이다 — APPROVED면 구매자에게 보이고
 * REJECTED면 가려진다. 그 전이는 상품 검수 큐 경로(승인·반려)가 소유한다.
 *
 * <p>adminSaleSuspended는 그와 다른 축이다 — 운영자가 검수와 무관한 이유로 판매를 잠근 상태이고,
 * 참인 동안 셀러의 판매 상태 변경이 거부된다.
 *
 * <p>storeName은 셀러 행이 없는 상품에서 null이다(products.seller_id는 FK 없는 값 참조다).
 */
public record AdminProductSummaryResponse(
        String productId,
        String productName,
        long sellerId,
        String storeName,
        String categoryName,
        long regularPrice,
        Long discountPrice,
        SaleStatus saleStatus,
        InspectionStatus inspectionStatus,
        long reportCount,
        boolean adminSaleSuspended,
        LocalDateTime adminSaleSuspendedAt,
        String adminSaleSuspendReason,
        LocalDateTime createdAt) {

    public static AdminProductSummaryResponse from(AdminProductSummaryResult result) {
        return new AdminProductSummaryResponse(
                result.productId(),
                result.productName(),
                result.sellerId(),
                result.storeName(),
                result.categoryName(),
                result.regularPrice(),
                result.discountPrice(),
                result.saleStatus(),
                result.inspectionStatus(),
                result.reportCount(),
                result.adminSaleSuspended(),
                result.adminSaleSuspendedAt(),
                result.adminSaleSuspendReason(),
                result.createdAt());
    }
}
