package com.aechak.infra.persistence.messaging

import com.aechak.application.messaging.ProcessedMessages
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class ProcessedMessageAdapter(
    private val db: JdbcClient,
) : ProcessedMessages {
    override fun markProcessed(
        consumer: String,
        eventId: String,
    ): Boolean =
        try {
            db
                .sql(
                    """
                    INSERT INTO processed_message (consumer, event_id) VALUES (:consumer, :eventId)
                    """.trimIndent(),
                ).param("consumer", consumer)
                .param("eventId", eventId)
                .update()
            true
        } catch (e: DuplicateKeyException) {
            false
        }
}
