package com.aechak.domain.order.cart

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "cart_items")
class CartItem protected constructor(
    optionCombinationId: Long,
    quantity: Int,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false)
    val optionCombinationId: Long = optionCombinationId

    @Column(nullable = false)
    var quantity: Int = quantity
        protected set

    companion object {
        fun of(
            optionCombinationId: Long,
            quantity: Int,
        ): CartItem {
            if (quantity < 1) {
                throw BusinessException(OrderErrorCode.INVALID_CART_ITEM_QUANTITY)
            }
            return CartItem(optionCombinationId, quantity)
        }
    }
}
