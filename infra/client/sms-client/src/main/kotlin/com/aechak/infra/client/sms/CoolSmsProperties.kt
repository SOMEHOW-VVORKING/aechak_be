package com.aechak.infra.client.sms

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * CoolSMS 발송 설정(sms.coolsms.*). 실발송 프로필에서만 바인딩되며, 미주입 항목이 있으면 부팅 실패(fail-fast).
 */
@ConfigurationProperties("sms.coolsms")
data class CoolSmsProperties(
    val apiKey: String,
    val apiSecret: String,
    /** 발신번호 — CoolSMS 콘솔에 등록·인증된 번호만 발송이 허용된다. */
    val from: String,
    /** SOLAPI 계열 공용 SDK라 API 도메인을 CoolSMS로 지정한다. */
    val domain: String = "https://api.coolsms.co.kr",
)
