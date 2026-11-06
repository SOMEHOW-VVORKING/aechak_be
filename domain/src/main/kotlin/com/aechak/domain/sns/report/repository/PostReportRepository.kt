package com.aechak.domain.sns.report.repository

import com.aechak.domain.sns.report.PostReport

interface PostReportRepository {
    /** 저장하고 즉시 flush한다. 같은 신고자가 같은 게시물을 이미 신고했으면 DuplicatePostReportException을 던진다. */
    fun save(report: PostReport): PostReport
}
