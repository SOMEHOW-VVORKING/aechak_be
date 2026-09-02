package com.aechak.application.inquiry.port

/**
 * 문의 통지 정책
 */
interface InquiryNotificationPolicy {
    val enabled: Boolean
    val recipients: List<String>
}
