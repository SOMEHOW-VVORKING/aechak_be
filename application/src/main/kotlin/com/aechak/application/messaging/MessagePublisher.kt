package com.aechak.application.messaging

import com.aechak.domain.support.DomainEvent

interface MessagePublisher {
    fun publish(
        aggregateType: String,
        aggregateId: String,
        event: DomainEvent,
    )
}
