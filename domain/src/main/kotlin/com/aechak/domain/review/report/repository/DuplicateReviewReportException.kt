package com.aechak.domain.review.report.repository

/** 같은 신고자가 같은 리뷰를 이미 신고한 경우 발생한다. */
class DuplicateReviewReportException(
    cause: Throwable,
) : RuntimeException(cause)
