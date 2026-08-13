package com.aechak.infra.client.sms

import com.aechak.application.user.verification.port.SmsSender
import com.solapi.sdk.message.model.Message
import com.solapi.sdk.message.service.DefaultMessageService

/**
 * CoolSMS 실발송 어댑터. 발송 실패는 SDK 예외를 그대로 던진다 — 정책 번역(에러코드·보상)은 application 담당.
 */
class CoolSmsSender(
    private val messageService: DefaultMessageService,
    private val from: String,
) : SmsSender {
    override fun send(
        phoneNumber: String,
        message: String,
    ) {
        messageService.send(Message(from = from, to = phoneNumber, text = message))
    }
}
