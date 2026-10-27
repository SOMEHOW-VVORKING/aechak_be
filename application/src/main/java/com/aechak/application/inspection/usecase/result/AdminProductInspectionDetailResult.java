package com.aechak.application.inspection.usecase.result;

import com.aechak.application.inspection.port.view.ReviewTargetProductView;
import com.aechak.application.inspection.service.InspectionSnapshot;
import com.aechak.application.inspection.service.OpenProductReviewSnapshot;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.product.product.enums.InspectionStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 검수 필드는 상품이 한 번도 검수를 타지 않았으면 전부 null임. 이의 필드는 열린 행에 이의가 걸렸을 때만 참.
 *
 * <p>사유 셋({@code reasonCode}·{@code reasonSummary}·{@code sellerNotice})은 지금 이 검토 행을
 * 막고 있는 판정에서 읽는다. 가장 최근 검수에서 읽으면 모델이 답을 못 준 뒤(판정 실패·기한 만료·
 * 증거 없는 통과) 사유가 비어서, 관리자가 이 행을 열어도 무엇을 풀지 고를 근거가 화면에 없다.
 * 정본 설계에서 이 행이 AI 반려를 푸는 유일한 자리라 그 공백이 곧 해제 불가가 된다.
 */
public record AdminProductInspectionDetailResult(
        String productId,
        String productName,
        String sellerName,
        List<String> categoryNames,
        String description,
        String representativeImageUrl,
        List<String> optionNames,
        InspectionStatus inspectionStatus,
        boolean awaitingAdminReview,
        LocalDateTime appealedAt,
        String appealReason,
        InspectionProcessingState processingState,
        InspectionJudgment judgment,
        String reasonCode,
        String reasonSummary,
        String sellerNotice,
        LocalDateTime requestedAt,
        LocalDateTime settledAt,
        List<ProductRejectionResult> rejections) {

    public static AdminProductInspectionDetailResult of(
            ReviewTargetProductView product,
            String representativeImageUrl,
            InspectionSnapshot snapshot,
            OpenProductReviewSnapshot openReview,
            InspectionSnapshot blocking,
            List<ProductRejectionResult> rejections) {
        InspectionSnapshot blame = blocking != null ? blocking : snapshot;
        return new AdminProductInspectionDetailResult(
                product.getProductPublicId(),
                product.getProductName(),
                product.getSellerName(),
                product.getCategoryNames(),
                product.getDescription(),
                representativeImageUrl,
                product.getOptionNames(),
                product.getInspectionStatus(),
                openReview != null,
                openReview == null ? null : openReview.getAppealedAt(),
                openReview == null ? null : openReview.getAppealReason(),
                snapshot == null ? null : snapshot.getProcessingState(),
                snapshot == null ? null : snapshot.getJudgment(),
                blame == null ? null : blame.getReasonCode(),
                blame == null ? null : blame.getReasonSummary(),
                blame == null ? null : blame.getSellerNotice(),
                snapshot == null ? null : snapshot.getRequestedAt(),
                snapshot == null ? null : snapshot.getSettledAt(),
                rejections);
    }
}
