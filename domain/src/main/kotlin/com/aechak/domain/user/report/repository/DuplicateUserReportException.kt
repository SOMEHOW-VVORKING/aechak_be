package com.aechak.domain.user.report.repository

/** 같은 신고자가 같은 사용자를 이미 신고한 경우 발생한다. */
class DuplicateUserReportException(
    cause: Throwable,
) : RuntimeException(cause)
