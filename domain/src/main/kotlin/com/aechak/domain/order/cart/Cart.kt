package com.aechak.domain.order.cart

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "carts",
    uniqueConstraints = [UniqueConstraint(name = "uk_carts_buyer_id", columnNames = ["buyer_id"])],
)
class Cart protected constructor(
    buyerId: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(name = "buyer_id", nullable = false)
    val buyerId: Long = buyerId

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "cart_id", nullable = false, updatable = false)
    private val _items: MutableList<CartItem> = mutableListOf()
    val items: List<CartItem> get() = _items.toList()

    fun addItem(
        optionCombinationId: Long,
        quantity: Int,
    ): CartItem {
        val existItem = _items.find { it.optionCombinationId == optionCombinationId }

        if (existItem != null) {
            existItem.accumulate(quantity)
            return existItem
        }

        if (_items.size >= MAX_ITEM_KINDS) {
            throw BusinessException(OrderErrorCode.CART_ITEM_LIMIT_EXCEEDED)
        }

        return CartItem.of(optionCombinationId, quantity).also { _items.add(it) }
    }

    companion object {
        /** 품목 종류 수 상한. 라인당 수량 상한(99)과 단위가 다름. */
        const val MAX_ITEM_KINDS = 100

        fun create(buyerId: Long): Cart = Cart(buyerId)
    }
}
