package com.aechak.application.email.port

data class EmailMessage(
    val to: List<String>,
    val replyTo: String?,
    val subject: String,
    val body: String,
)
