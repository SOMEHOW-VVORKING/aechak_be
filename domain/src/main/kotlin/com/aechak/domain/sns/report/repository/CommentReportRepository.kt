package com.aechak.domain.sns.report.repository

import com.aechak.domain.sns.report.CommentReport

interface CommentReportRepository {
    /** 저장하고 즉시 flush한다. 같은 신고자가 같은 댓글을 이미 신고했으면 DuplicateCommentReportException을 던진다. */
    fun save(report: CommentReport): CommentReport
}
