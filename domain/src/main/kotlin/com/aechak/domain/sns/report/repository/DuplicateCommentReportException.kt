package com.aechak.domain.sns.report.repository

/** 같은 신고자가 같은 댓글을 이미 신고한 경우 발생한다. */
class DuplicateCommentReportException(
    cause: Throwable,
) : RuntimeException(cause)
