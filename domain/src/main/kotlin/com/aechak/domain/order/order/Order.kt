package com.aechak.domain.order.order

import com.aechak.common.error.BusinessException
import com.aechak.domain.support.Ulid
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.ConfirmType

@Entity
@Table(
    name = "orders",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_orders_public_id", columnNames = ["public_id"]),
    ],
)
class Order protected constructor(
    orderGroup: OrderGroup,
    sellerId: Long,
    allocatedCouponDiscount: Int,
    sellerShippingFee: Int,
) : AggregateRoot() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false, length = 26)
    val publicId: String = Ulid.generate()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_group_id", nullable = false)
    val orderGroup: OrderGroup = orderGroup

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: OrderStatus = OrderStatus.PENDING_PAYMENT
        protected set

    @Column(nullable = false)
    val sellerId: Long = sellerId

    @Column(length = 255)
    var sellerNameSnapshot: String? = null
        protected set

    @Column(nullable = false)
    val allocatedCouponDiscount: Int = allocatedCouponDiscount

    @Column(nullable = false)
    val sellerShippingFee: Int = sellerShippingFee

    @Column
    var purchaseConfirmedAt: LocalDateTime? = null
        protected set

    @Column
    var purchaseConfirmNotifiedAt: LocalDateTime? = null
        protected set

    @Column
    var autoConfirmDueAt: LocalDateTime? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var confirmType: ConfirmType? = null
        protected set

    @Column(nullable = false)
    var extensionCount: Int = 0
        protected set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private val _items: MutableList<OrderItem> = mutableListOf()
    val items: List<OrderItem> get() = _items.toList()

    fun markPaid() {
        transitionTo(OrderStatus.PAID)
    }

    fun cancelUnpaid() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION)
        }
        transitionTo(OrderStatus.CANCELLED)
    }

    fun cancel() {
        if (!status.canCancel()) {
            throw BusinessException(OrderErrorCode.CANNOT_CANCEL_SHIPPED)
        }
        transitionTo(OrderStatus.CANCELLED)
    }

    fun confirmPurchase(confirmType: ConfirmType) {
        transitionTo(OrderStatus.PURCHASE_CONFIRMED)
        purchaseConfirmedAt = LocalDateTime.now()
        this.confirmType = confirmType
    }

    private fun transitionTo(next: OrderStatus) {
        if (!status.canTransitionTo(next)) {
            throw BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION)
        }
        status = next
    }

    companion object {
        fun place(
            orderGroup: OrderGroup,
            sellerId: Long,
            allocatedCouponDiscount: Int,
            sellerShippingFee: Int,
            items: List<OrderItem>,
        ): Order {
            return Order(orderGroup, sellerId, allocatedCouponDiscount, sellerShippingFee).apply {
                items.forEach { _items += it }
            }
        }
    }
}
