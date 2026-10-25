package com.aechak.application.inspection.facade;

import com.aechak.application.file.usecase.FileUseCase;
import com.aechak.application.inspection.port.view.ReviewTargetProductView;
import com.aechak.application.inspection.service.InspectionQueryService;
import com.aechak.application.inspection.service.ProductReviewQueueService;
import com.aechak.application.inspection.usecase.AdminProductInspectionUseCase;
import com.aechak.application.inspection.usecase.result.AdminProductInspectionDetailResult;
import com.aechak.application.inspection.usecase.result.AdminProductInspectionSummaryResult;
import com.aechak.application.inspection.usecase.result.ProductRejectionResult;
import com.aechak.application.support.PageQuery;
import com.aechak.application.support.PageResult;
import com.aechak.domain.inspection.enums.ReviewQueueStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductInspectionFacade implements AdminProductInspectionUseCase {

    private final ProductReviewQueueService reviewQueueService;
    private final InspectionQueryService inspectionQueryService;
    private final FileUseCase fileUseCase;

    @Transactional(readOnly = true)
    @Override
    public PageResult<AdminProductInspectionSummaryResult> list(ReviewQueueStatus status, PageQuery pageQuery) {
        return new PageResult<>(
                reviewQueueService.findPage(status, pageQuery).stream()
                        .map(AdminProductInspectionSummaryResult::from)
                        .toList(),
                reviewQueueService.count(status));
    }

    @Transactional(readOnly = true)
    @Override
    public AdminProductInspectionDetailResult detail(String productPublicId) {
        ReviewTargetProductView product = reviewQueueService.getReviewTarget(productPublicId);
        long productId = product.getProductId();
        return AdminProductInspectionDetailResult.of(
                product,
                fileUseCase.resolveMediaUrl(product.getRepresentativeImageKey()),
                inspectionQueryService.findLatestProductInspection(productId),
                inspectionQueryService.isAwaitingAdminReview(productId),
                reviewQueueService.findRejected(productId).stream()
                        .map(ProductRejectionResult::from)
                        .toList());
    }

    @Transactional
    @Override
    public void approve(long adminId, String productPublicId) {
        long productId = reviewQueueService.getReviewTarget(productPublicId).getProductId();
        reviewQueueService.approveByAdmin(productId, LocalDateTime.now());
        log.info("관리자가 상품 검수를 승인했다. adminId={} productId={}", adminId, productId);
    }

    @Transactional
    @Override
    public void reject(long adminId, String productPublicId, String reason) {
        long productId = reviewQueueService.getReviewTarget(productPublicId).getProductId();
        reviewQueueService.rejectByAdmin(productId, reason, LocalDateTime.now());
        log.info("관리자가 상품 검수를 반려했다. adminId={} productId={}", adminId, productId);
    }
}
