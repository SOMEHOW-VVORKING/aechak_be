package com.aechak.application.messaging

/**
 * 인박스 포트
 */
interface ProcessedMessages {
    /**
     * return 값이 false면 이미 처리된 메세지이므로 스킵함.
     */
    fun markProcessed(
        consumer: String,
        eventId: String,
    ): Boolean
}
