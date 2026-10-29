package com.aechak.admin.review.request;

import com.aechak.application.review.admin.usecase.query.AdminReviewSearchQuery;
import com.aechak.application.support.CursorPageSize;
import com.aechak.domain.review.review.enums.ReviewStatus;
import org.hibernate.validator.constraints.Range;

/**
 * 운영자 리뷰 목록 조건.
 *
 * <p>status는 리뷰 노출 상태를 그대로 받고, reported=true는 신고가 쌓인 행만 좁힌다. 두 축은
 * 직교한다 — 노출 중이면서 신고가 쌓인 리뷰가 운영자의 작업 목록이다. keyword는 리뷰 본문과 상품명
 * 양쪽을 본다. productId는 상품 공개 식별자(ULID)다.
 */
public record AdminReviewSearchRequest(
        String productId,
        @Range(min = 1, max = 5, message = "rating은 {min}~{max} 사이여야 합니다.") Integer rating,
        ReviewStatus status,
        Boolean reported,
        String keyword,
        String cursor,
        @Range(min = CursorPageSize.MIN, max = CursorPageSize.MAX, message = "size는 {min}~{max} 사이여야 합니다.")
                Integer size) {

    public AdminReviewSearchQuery toQuery() {
        return new AdminReviewSearchQuery(
                productId,
                rating,
                status,
                Boolean.TRUE.equals(reported),
                keyword,
                cursor,
                size == null ? CursorPageSize.DEFAULT : size);
    }
}
