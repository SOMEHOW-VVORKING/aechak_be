package com.aechak.application.review.admin.port.view;

import com.aechak.domain.review.review.enums.ReviewBlindActor;
import com.aechak.domain.review.review.enums.ReviewBlindReason;
import com.aechak.domain.review.review.enums.ReviewStatus;
import java.time.LocalDateTime;

/**
 * 운영자 시점 리뷰 한 행. 본문은 원문(content)과 마스킹 대체 본문(displayContent)을 모두 들고 온다 —
 * 운영자는 구매자에게 무엇이 보이는지와 작성자가 무엇을 썼는지를 함께 봐야 판단할 수 있다.
 */
public record AdminReviewView(
        long reviewId,
        long productId,
        String productPublicId,
        String productName,
        String sellerName,
        long authorUserId,
        int rating,
        String content,
        String displayContent,
        ReviewStatus reviewStatus,
        String optionNameSnapshot,
        ReviewBlindActor blindActor,
        ReviewBlindReason blindReasonCode,
        String blindReasonText,
        LocalDateTime blindedAt,
        Long blindedByAdminId,
        Long deletedByAdminId,
        LocalDateTime deletedAt,
        LocalDateTime createdAt) {}
