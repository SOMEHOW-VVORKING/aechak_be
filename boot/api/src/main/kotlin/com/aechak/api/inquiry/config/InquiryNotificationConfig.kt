package com.aechak.api.inquiry.config

import com.aechak.infra.ses.SesProperties
import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 문의 통지 설정
 * enabled면 수신자와 발신 주소가 있어야 부팅 가능, 로컬은 enabled=false(기본)면 발송 생략하고 정상 기동
 */
@Configuration
@EnableConfigurationProperties(InquiryNotificationProperties::class)
class InquiryNotificationConfig(
    private val notification: InquiryNotificationProperties,
    private val sesProperties: SesProperties,
) {
    @PostConstruct
    fun validate() {
        if (!notification.enabled) return
        val missing =
            buildList {
                if (notification.recipients.isEmpty()) add("inquiry.notification.recipients")
                if (sesProperties.from.isBlank()) add("aws.ses.from")
            }
        check(missing.isEmpty()) {
            "inquiry.notification.enabled=true 이면 ${missing.joinToString()} 필수 (끄려면 enabled=false)"
        }
    }
}
