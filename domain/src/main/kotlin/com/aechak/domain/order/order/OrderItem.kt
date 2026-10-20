package com.aechak.domain.order.order

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
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

    /** 주문 취소 또는 클레임 환불 집행에 따른 품목 취소. */
    fun markCancelled() {
        transitionTo(OrderItemStatus.CANCELLED)
    }

    /** 반품 요청 접수. */
    fun markReturnRequested() {
        transitionTo(OrderItemStatus.RETURN_REQUESTED)
    }

    /** 회수 완료. */
    fun markReturned() {
        transitionTo(OrderItemStatus.RETURNED)
    }

    /** 교환 요청 접수. */
    fun markExchangeRequested() {
        transitionTo(OrderItemStatus.EXCHANGE_REQUESTED)
    }

    /** 거부·철회된 반품·교환 요청의 되돌림. */
    fun revertClaimRequest() {
        transitionTo(OrderItemStatus.ORDERED)
    }

    private fun transitionTo(next: OrderItemStatus) {
        if (!itemStatus.canTransitionTo(next)) {
            throw BusinessException(OrderErrorCode.INVALID_ORDER_ITEM_STATUS_TRANSITION)
        }
        itemStatus = next
    }

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
