package com.aechak.application.review.admin.service;

import com.aechak.common.error.BusinessException;
import com.aechak.domain.review.error.ReviewErrorCode;
import com.aechak.domain.review.review.Review;
import com.aechak.domain.review.review.enums.ReviewBlindReason;
import com.aechak.domain.review.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 비노출 전이 보관함. 전이 규칙은 전부 애그리거트가 들고 있고 여기는 로딩·저장과
 * 재집계 대상 productId 반환만 한다.
 */
@Service
@RequiredArgsConstructor
public class AdminReviewService {

    private final ReviewRepository reviewRepository;

    /** 블라인드 후 평점 재집계 대상 productId */
    public long blind(long adminId, long reviewId, ReviewBlindReason reasonCode, String reasonText) {
        Review review = getById(reviewId);
        review.blindByAdmin(adminId, reasonCode, reasonText);
        reviewRepository.save(review);
        return review.getProductId();
    }

    /** 해제 후 평점 재집계 대상 productId */
    public long unblind(long reviewId) {
        Review review = getById(reviewId);
        review.unblind();
        reviewRepository.save(review);
        return review.getProductId();
    }

    /** 삭제 후 평점 재집계 대상 productId */
    public long delete(long adminId, long reviewId, ReviewBlindReason reasonCode, String reasonText) {
        Review review = getById(reviewId);
        review.deleteByAdmin(adminId, reasonCode, reasonText);
        reviewRepository.save(review);
        return review.getProductId();
    }

    private Review getById(long reviewId) {
        Review review = reviewRepository.findById(reviewId);
        if (review == null) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND, null, null);
        }
        return review;
    }
}
