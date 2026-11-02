package com.aechak.api.report.config

import com.aechak.application.report.port.ReportNotificationPolicy
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("report.notification")
data class ReportNotificationProperties(
    override val enabled: Boolean = false,
    override val recipients: List<String> = emptyList(),
) : ReportNotificationPolicy
