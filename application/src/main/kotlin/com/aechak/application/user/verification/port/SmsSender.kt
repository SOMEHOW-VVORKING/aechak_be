package com.aechak.application.user.verification.port

/**
 * 외부 SMS 발송 포트 — 발송 실패는 예외로 던지고, 정책 번역(에러코드·보상)은 application이 담당한다.
 * 실발송 프로필(dev·staging·prod)은 CoolSMS 어댑터가, 그 외는 발송 생략 어댑터가 맡는다 —
 * 실발송 프로필에서 CoolSMS 키 미주입이면 부팅 실패로 오배선을 차단한다.
 */
interface SmsSender {
    fun send(
        phoneNumber: String,
        message: String,
    )
}
