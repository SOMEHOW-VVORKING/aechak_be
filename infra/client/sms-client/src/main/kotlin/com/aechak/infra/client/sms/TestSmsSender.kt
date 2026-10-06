package com.aechak.infra.client.sms

import com.aechak.application.user.verification.port.SmsSender
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 발송 생략 어댑터 — 실발송 프로필(dev·staging·prod) 밖에서만 뜬다. 실제 발송 없이 로그만 남긴다.
 * 번호·코드는 로그에 싣지 않는다(PII·인증 정보).
 */
@Component
@Profile("!dev & !staging & !prod")
class TestSmsSender : SmsSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(
        phoneNumber: String,
        message: String,
    ) {
        log.info("SMS 발송 생략(테스트 어댑터) — messageLength={}", message.length)
    }
}
