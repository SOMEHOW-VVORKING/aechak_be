package com.aechak.application.sns.report.usecase.command

import com.aechak.domain.sns.report.enums.PostReportReason

data class SubmitCommentReportCommand(
    val reporterId: Long,
    val commentPublicId: String,
    val reasonCode: PostReportReason,
    val reasonText: String?,
)
