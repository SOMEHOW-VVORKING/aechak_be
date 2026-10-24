package com.aechak.application.inspection.usecase.result;

import com.aechak.domain.inspection.ProductReviewQueueItem;
import java.time.LocalDateTime;

/** 닫힌 행은 다시 고치지 않으므로 마지막 갱신 시각이 곧 반려 시각임 */
public record ProductRejectionResult(String reason, LocalDateTime rejectedAt) {

    public static ProductRejectionResult from(ProductReviewQueueItem item) {
        return new ProductRejectionResult(item.getRejectReason(), item.getUpdatedAt());
    }
}
