package com.aechak.domain.order.order

import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "order_items")
class OrderItem protected constructor(
    productId: Long,
    optionCombinationId: Long,
    optionNameSnapshot: String,
    quantity: Int,
    unitPriceSnapshot: Long,
    discountAllocatedAmount: Long,
    productVersionId: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false)
    val productId: Long = productId

    @Column(nullable = false)
    val optionCombinationId: Long = optionCombinationId

    @Column(nullable = false, length = 255)
    val optionNameSnapshot: String = optionNameSnapshot

    @Column(nullable = false)
    val quantity: Int = quantity

    @Column(nullable = false)
    val unitPriceSnapshot: Long = unitPriceSnapshot

    @Column(nullable = false)
    val discountAllocatedAmount: Long = discountAllocatedAmount

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var itemStatus: OrderItemStatus = OrderItemStatus.ORDERED
        protected set

    @Column(nullable = false)
    val productVersionId: Long = productVersionId

    /** 할인 안분액을 뺀 이 품목의 상품금액 — 구매확정 적립과 정산 원장이 같은 값을 기준으로 삼는다. */
    fun productAmountAfterDiscount(): Long = Math.multiplyExact(unitPriceSnapshot, quantity.toLong()) - discountAllocatedAmount

    companion object {
        fun of(
            productId: Long,
            optionCombinationId: Long,
            optionNameSnapshot: String,
            quantity: Int,
            unitPriceSnapshot: Long,
            discountAllocatedAmount: Long,
            productVersionId: Long,
        ): OrderItem =
            OrderItem(
                productId,
                optionCombinationId,
                optionNameSnapshot,
                quantity,
                unitPriceSnapshot,
                discountAllocatedAmount,
                productVersionId,
            )
    }
}
