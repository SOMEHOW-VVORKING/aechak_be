package com.aechak.domain.order.claim

import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "claim_items")
class ClaimItem protected constructor(
    orderItem: OrderItem,
    quantity: Int,
    refundAmount: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    val orderItem: OrderItem = orderItem

    @Column(nullable = false)
    val quantity: Int = quantity

    @Column(nullable = false)
    val refundAmount: Long = refundAmount

    companion object {
        fun of(
            orderItem: OrderItem,
            quantity: Int,
            refundAmount: Long,
        ): ClaimItem = ClaimItem(orderItem, quantity, refundAmount)
    }
}
