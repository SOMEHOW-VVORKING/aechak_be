package com.aechak.domain.user.report.repository

import com.aechak.domain.user.report.UserReport

interface UserReportRepository {
    /** 저장하고 즉시 flush한다. 같은 신고자가 같은 사용자를 이미 신고했으면 DuplicateUserReportException을 던진다. */
    fun save(report: UserReport): UserReport
}
