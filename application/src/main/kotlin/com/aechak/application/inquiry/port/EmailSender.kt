package com.aechak.application.inquiry.port

interface EmailSender {
    fun send(message: EmailMessage)
}
