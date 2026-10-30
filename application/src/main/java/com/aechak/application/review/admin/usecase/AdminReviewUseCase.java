package com.aechak.application.review.admin.usecase;

import com.aechak.application.review.admin.usecase.command.BlindReviewCommand;
import com.aechak.application.review.admin.usecase.command.DeleteReviewCommand;
import com.aechak.application.review.admin.usecase.command.UnblindReviewCommand;
import com.aechak.application.review.admin.usecase.query.AdminReviewSearchQuery;
import com.aechak.application.review.admin.usecase.result.AdminReviewDetailResult;
import com.aechak.application.review.admin.usecase.result.AdminReviewSummaryResult;
import com.aechak.application.support.CursorPageResult;

/**
 * 운영자 리뷰 관리 진입점. 구매자·셀러 유스케이스와 입구를 나눈 이유는 주입 의존이 겹치지 않기 때문이다 —
 * 이쪽은 상품·셀러·신고를 함께 읽고 비노출 전이를 쓴다.
 */
public interface AdminReviewUseCase {

    CursorPageResult<AdminReviewSummaryResult> list(AdminReviewSearchQuery query);

    AdminReviewDetailResult detail(long reviewId);

    void blind(BlindReviewCommand command);

    void unblind(UnblindReviewCommand command);

    void delete(DeleteReviewCommand command);
}
