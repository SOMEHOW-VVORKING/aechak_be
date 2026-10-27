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
 *
 * <p>전이 대상은 행을 잠가 읽는다(findByIdForUpdate). 잠그지 않으면 판정과 저장 사이에 다른 전이가
 * 끼어든다 — 운영자 A의 삭제와 B의 해제가 겹치면 둘 다 BLOCKED를 읽어 둘 다 204로 성공하고,
 * 나중에 쓰는 해제가 종결된 리뷰를 PUBLIC으로 되살린다. @Version으로는 막을 수 없다:
 * 거절된 전이(삭제된 리뷰의 블라인드 등)는 아무것도 쓰지 않아 버전이 올라가지 않는다
 * (ProductRepository.findByIdForUpdate와 같은 판단).
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
        Review review = reviewRepository.findByIdForUpdate(reviewId);
        if (review == null) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND, null, null);
        }
        return review;
    }
}
