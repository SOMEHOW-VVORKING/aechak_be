package com.aechak.message.order

import com.aechak.message.GuaranteedMessage
import java.time.Instant

data class OrderGroupCancelledMessage(
    val orderGroupPublicId: String,
    val buyerId: Long,
    val usedPoint: Long,
    val items: List<Item>,
    override val occurredAt: Instant = Instant.now(),
) : GuaranteedMessage {
    override val aggregateType: String get() = "order"
    override val aggregateId: String get() = orderGroupPublicId
    override val eventId: String get() = "order-group-$orderGroupPublicId:cancelled"

    data class Item(
        val optionCombinationId: Long,
        val quantity: Int,
    )
}
