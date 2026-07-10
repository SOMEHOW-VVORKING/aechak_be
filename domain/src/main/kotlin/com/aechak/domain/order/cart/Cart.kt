package com.aechak.domain.order.cart

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

    fun addItem(item: CartItem) {
        _items += item
    }

    fun removeItem(itemId: Long) {
        _items.removeIf { it.id == itemId }
    }

    companion object {
        fun create(buyerId: Long): Cart = Cart(buyerId)
    }
}
