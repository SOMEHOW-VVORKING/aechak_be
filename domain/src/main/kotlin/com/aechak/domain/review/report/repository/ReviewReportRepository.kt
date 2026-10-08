package com.aechak.domain.review.report.repository

import com.aechak.domain.review.report.ReviewReport

interface ReviewReportRepository {
    /** 저장하고 즉시 flush한다. 같은 신고자가 같은 리뷰를 이미 신고했으면 DuplicateReviewReportException을 던진다. */
    fun save(report: ReviewReport): ReviewReport
}
