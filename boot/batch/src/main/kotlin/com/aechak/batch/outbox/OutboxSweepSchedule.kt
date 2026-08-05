package com.aechak.batch.outbox

import com.aechak.infra.kafka.outbox.OutboxSweepTrigger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxSweepSchedule(
    private val sweeper: OutboxSweepTrigger,
) {
    @Scheduled(fixedDelay = 5000)
    fun poll() {
        sweeper.sweepNow()
    }
}
