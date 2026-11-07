package com.aechak.application.product.product.admin.usecase.result;

import com.aechak.application.product.product.admin.port.view.AdminProductView;
import com.aechak.domain.product.product.enums.InspectionStatus;
import com.aechak.domain.product.product.enums.SaleStatus;
import java.time.LocalDateTime;

/**
 * 어드민 상품 목록의 한 행.
 *
 * @param adminSaleSuspended 운영자가 막아 둔 상품인지. 이 값이 참이면 셀러의 판매 상태 변경이 거부된다
 */
public record AdminProductSummaryResult(
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

    public static AdminProductSummaryResult from(AdminProductView view) {
        return new AdminProductSummaryResult(
                view.productPublicId(),
                view.productName(),
                view.sellerId(),
                view.storeName(),
                view.categoryName(),
                view.regularPrice(),
                view.discountPrice(),
                view.saleStatus(),
                view.inspectionStatus(),
                view.reportCount(),
                view.adminSaleBlockedAt() != null,
                view.adminSaleBlockedAt(),
                view.adminSaleBlockReason(),
                view.createdAt());
    }
}
