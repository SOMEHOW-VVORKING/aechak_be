package com.aechak.application.inspection.facade;

import com.aechak.application.file.usecase.FileUseCase;
import com.aechak.application.inspection.port.view.ReviewTargetProductView;
import com.aechak.application.inspection.service.InspectionQueryService;
import com.aechak.application.inspection.service.OpenProductReviewSnapshot;
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
import org.springframework.transaction.annotation.Isolation;
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
        OpenProductReviewSnapshot openReview = inspectionQueryService.findOpenProductReview(productId);
        return AdminProductInspectionDetailResult.of(
                product,
                fileUseCase.resolveMediaUrl(product.getRepresentativeImageKey()),
                inspectionQueryService.findLatestProductInspection(productId),
                openReview,
                openReview == null
                        ? null
                        : inspectionQueryService.findBlockingProductInspection(productId, openReview.getBlindedAt()),
                reviewQueueService.findRejected(productId).stream()
                        .map(ProductRejectionResult::from)
                        .toList());
    }

    /**
     * 관리자 승인. 열린 검토 행을 닫고 상품을 노출로 돌린다.
     *
     * <p>READ COMMITTED로 연다. 이 경로는 열린 행을 잠금 조회로 집는데, 그 조회의 인덱스가
     * (대상 종류, 대상 id, 상태) 비유일 인덱스라서 기본 격리(REPEATABLE READ)에서는 행이 없을 때
     * 꼬리 간극을 잠근다. 그 간극이 곧 판정 경로의 첫 큐 등재 INSERT 자리다 — 운영자가 승인·반려를
     * 한 번 누르는 동안 그 트랜잭션이 사는 내내 모든 신규 상품의 첫 등재가 선다. 판정 소비자는
     * 동시성 여럿으로 돌고 잠금 대기가 넘치면 예외로 떨어져 재시도·격리 토픽으로 흐른다.
     * 같은 조회에 닿는 다른 경로(판정 컨슈머·판정 적용·셀러 이의)는 모두 같은 선언을 들고 있다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public void approve(long adminId, String productPublicId) {
        long productId = reviewQueueService.getReviewTarget(productPublicId).getProductId();
        reviewQueueService.approveByAdmin(productId, LocalDateTime.now());
        log.info("관리자가 상품 검수를 승인했다. adminId={} productId={}", adminId, productId);
    }

    /** 관리자 반려. 격리 수준의 근거는 {@link #approve(long, String)}과 같다 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public void reject(long adminId, String productPublicId, String reason) {
        long productId = reviewQueueService.getReviewTarget(productPublicId).getProductId();
        reviewQueueService.rejectByAdmin(productId, reason, LocalDateTime.now());
        log.info("관리자가 상품 검수를 반려했다. adminId={} productId={}", adminId, productId);
    }
}
