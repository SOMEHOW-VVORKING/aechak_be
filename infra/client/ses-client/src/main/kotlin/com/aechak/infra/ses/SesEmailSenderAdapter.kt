package com.aechak.infra.ses

import com.aechak.application.inquiry.port.EmailMessage
import com.aechak.application.inquiry.port.EmailSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mail.MailSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.stereotype.Component

@Component
class SesEmailSenderAdapter(
    private val mailSenderProvider: ObjectProvider<MailSender>,
    private val sesProperties: SesProperties,
) : EmailSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(message: EmailMessage) {
        val mailSender = mailSenderProvider.ifAvailable
        if (mailSender == null) {
            log.warn("MailSender 미구성으로 메일 발송 생략 (SES 비활성 등)")
            return
        }
        val mail =
            SimpleMailMessage().apply {
                from = sesProperties.from
                setTo(*message.to.toTypedArray())
                message.replyTo?.let { replyTo = it }
                subject = message.subject
                text = message.body
            }
        mailSender.send(mail)
    }
}
