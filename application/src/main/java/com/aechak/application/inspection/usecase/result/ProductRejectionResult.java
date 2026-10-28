package com.aechak.application.inspection.usecase.result;

import com.aechak.domain.inspection.ProductReviewQueueItem;
import java.time.LocalDateTime;

/** rejectedAt은 행이 닫힌 시각임 */
public record ProductRejectionResult(String reason, LocalDateTime rejectedAt) {

    public static ProductRejectionResult from(ProductReviewQueueItem item) {
        return new ProductRejectionResult(item.getRejectReason(), item.getResolvedAt());
    }
}
