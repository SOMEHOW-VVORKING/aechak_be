package com.aechak.api.inquiry.config

import com.aechak.application.inquiry.port.InquiryNotificationPolicy
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("inquiry.notification")
data class InquiryNotificationProperties(
    override val enabled: Boolean = false,
    override val recipients: List<String> = emptyList(),
) : InquiryNotificationPolicy
