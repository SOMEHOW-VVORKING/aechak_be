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

    /**
     * 검토 큐 목록. {@code appealedOnly}는 셀러가 이의를 접수한 행만 본다.
     *
     * <p>이의 필터에 상태를 주지 않으면 열린 행으로 좁힌다. 이의 목록은 관리자의 작업 목록이고,
     * 조건을 안 붙이면 한 번이라도 이의가 걸렸던 행 전부 — 이미 승인·반려·대체로 닫힌 것까지 —
     * 가 섞여 와서 행마다 상태를 눈으로 가려야 한다. 닫힌 이의 이력을 보려면 상태를 명시한다.
     */
    @Transactional(readOnly = true)
    @Override
    public PageResult<AdminProductInspectionSummaryResult> list(
            ReviewQueueStatus status, boolean appealedOnly, PageQuery pageQuery) {
        ReviewQueueStatus effective = appealedOnly && status == null ? ReviewQueueStatus.PENDING_REVIEW : status;
        return new PageResult<>(
                reviewQueueService.findPage(effective, appealedOnly, pageQuery).stream()
                        .map(AdminProductInspectionSummaryResult::from)
                        .toList(),
                reviewQueueService.count(effective, appealedOnly));
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
                inspectionQueryService.findOpenProductReview(productId),
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
