package com.aechak.application.review.admin.port;

import com.aechak.application.review.admin.port.view.AdminReviewCountView;
import com.aechak.application.review.admin.port.view.AdminReviewReportView;
import com.aechak.application.review.admin.port.view.AdminReviewView;
import java.util.Collection;
import java.util.List;

public interface AdminReviewQueryPort {

    List<AdminReviewView> findPage(AdminReviewListCondition condition);

    long count(AdminReviewListCondition condition);

    /** 없으면 null. 삭제된 리뷰도 돌려준다 — 운영자는 지워진 리뷰의 처리 기록을 봐야 한다. */
    AdminReviewView findById(long reviewId);

    List<AdminReviewCountView> countPhotosByReviewIds(Collection<Long> reviewIds);

    List<AdminReviewCountView> countReportsByReviewIds(Collection<Long> reviewIds);

    List<AdminReviewReportView> findReportsByReviewId(long reviewId);
}
