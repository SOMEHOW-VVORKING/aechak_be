package com.aechak.application.email.port

interface EmailSender {
    fun send(message: EmailMessage)
}
