package com.aechak.application.report.port

/**
 * 신고 통지 정책
 */
interface ReportNotificationPolicy {
    val enabled: Boolean
    val recipients: List<String>
}
