package com.aechak.api.inquiry

import com.aechak.application.email.port.EmailMessage
import com.aechak.application.email.port.EmailSender

/** SES 실발송 대체 기록 Fake. 마지막 메시지 보관, failOnSend로 발송 실패 흉내 */
class RecordingEmailSender : EmailSender {
    @Volatile
    var last: EmailMessage? = null

    @Volatile
    var failOnSend: Boolean = false

    override fun send(message: EmailMessage) {
        last = message
        if (failOnSend) throw RuntimeException("SES 발송 실패 (테스트)")
    }

    fun reset() {
        last = null
        failOnSend = false
    }
}
