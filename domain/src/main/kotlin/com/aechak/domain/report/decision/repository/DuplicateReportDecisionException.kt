package com.aechak.domain.report.decision.repository

/** 같은 신고에 결정이 이미 있다 — UNIQUE(target_type, report_id) 위반을 어댑터가 이 예외로 바꾼다. */
class DuplicateReportDecisionException(
    cause: Throwable,
) : RuntimeException(cause)
