package com.aechak.domain.sns.report.repository

/** 같은 신고자가 같은 게시물을 이미 신고한 경우 발생한다. */
class DuplicatePostReportException(
    cause: Throwable,
) : RuntimeException(cause)
