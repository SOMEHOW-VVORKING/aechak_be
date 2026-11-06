package com.aechak.application.sns.report.usecase.command

import com.aechak.domain.sns.report.enums.PostReportReason

data class SubmitPostReportCommand(
    val reporterId: Long,
    val postPublicId: String,
    val reasonCode: PostReportReason,
    val reasonText: String?,
)
