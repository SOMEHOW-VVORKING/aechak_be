package com.aechak.api.report.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** 신고 통지 설정 바인딩. 수신자가 없어도 기동하고 발송만 생략한다 */
@Configuration
@EnableConfigurationProperties(ReportNotificationProperties::class)
class ReportNotificationConfig
