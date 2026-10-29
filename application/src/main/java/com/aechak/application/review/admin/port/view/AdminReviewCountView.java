package com.aechak.application.review.admin.port.view;

/** 리뷰별 부속 건수(사진 수·신고 수) 배치 조회 결과. */
public record AdminReviewCountView(long reviewId, long count) {}
