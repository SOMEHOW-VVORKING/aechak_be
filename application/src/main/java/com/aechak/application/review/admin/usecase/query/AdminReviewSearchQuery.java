package com.aechak.application.review.admin.usecase.query;

import com.aechak.domain.review.review.enums.ReviewStatus;

/**
 * 운영자 리뷰 목록 조건. status는 리뷰 상태를 그대로 받고(화면 어휘로 접는 것은 클라이언트 몫),
 * reportedOnly는 상태와 직교하는 축이다 — 노출 중이면서 신고가 쌓인 리뷰가 운영자의 작업 목록이다.
 */
public record AdminReviewSearchQuery(
        String productPublicId,
        Integer rating,
        ReviewStatus status,
        boolean reportedOnly,
        String keyword,
        String cursor,
        int size) {}
