package com.aechak.application.user.verification.port

/**
 * 외부 SMS 발송 포트 — 발송 실패는 예외로 던지고, 정책 번역(에러코드·보상)은 application이 담당한다.
 * prod 프로필에는 실벤더 어댑터가 등록되기 전까지 구현 빈이 없다 — 테스트 모드가 운영에 살아남는 경로를 기동 실패로 차단.
 */
interface SmsSender {
    fun send(
        phoneNumber: String,
        message: String,
    )
}
