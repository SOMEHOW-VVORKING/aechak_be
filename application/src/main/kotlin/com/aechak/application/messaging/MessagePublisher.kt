package com.aechak.application.messaging

import com.aechak.message.IntegrationMessage

interface MessagePublisher {
    fun publish(message: IntegrationMessage)
}
