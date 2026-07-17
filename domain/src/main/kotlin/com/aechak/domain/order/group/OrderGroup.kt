package com.aechak.domain.order.group

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "order_groups",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_order_group_public_id", columnNames = ["public_id"]),
        UniqueConstraint(name = "uk_order_group_idempotency_key", columnNames = ["idempotency_key"]),
    ],
)
class OrderGroup protected constructor(
    buyerId: Long,
    usedPoint: Long,
    totalProductAmount: Long,
    totalShippingFee: Long,
    finalPaymentAmount: Long,
    idempotencyKey: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false, length = 26)
    val publicId: String = Ulid.generate()

    @Column(nullable = false)
    val buyerId: Long = buyerId

    @Column
    var deliveryAddressId: Long? = null
        protected set

    @Column(length = 255)
    var receiverNameEnc: String? = null
        protected set

    @Column(length = 255)
    var contactNumberEnc: String? = null
        protected set

    @Column(length = 255)
    var zipCode: String? = null
        protected set

    @Column(length = 512)
    var baseAddress: String? = null
        protected set

    @Column(length = 512)
    var detailAddress: String? = null
        protected set

    @Column(length = 255)
    var deliveryMemo: String? = null
        protected set

    @Column
    var appliedCouponId: Long? = null
        protected set

    @Column(nullable = false)
    var couponDiscountAmount: Long = 0
        protected set

    @Column(nullable = false)
    var usedPoint: Long = usedPoint
        protected set

    @Column(nullable = false)
    var totalProductAmount: Long = totalProductAmount
        protected set

    @Column(nullable = false)
    var totalShippingFee: Long = totalShippingFee
        protected set

    @Column(nullable = false)
    var finalPaymentAmount: Long = finalPaymentAmount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: OrderGroupStatus = OrderGroupStatus.PENDING_PAYMENT
        protected set

    @Column
    var expiresAt: LocalDateTime? = null
        protected set

    @Column(name = "idempotency_key", nullable = false, length = 100)
    val idempotencyKey: String = idempotencyKey

    fun isExpired(now: LocalDateTime = LocalDateTime.now()): Boolean = expiresAt?.isBefore(now) ?: false

    fun markPaid() {
        if (status != OrderGroupStatus.PENDING_PAYMENT) {
            throw BusinessException(OrderErrorCode.INVALID_ORDER_GROUP_STATUS_TRANSITION)
        }
        status = OrderGroupStatus.PAID
    }

    companion object {
        fun create(
            buyerId: Long,
            usedPoint: Long,
            totalProductAmount: Long,
            totalShippingFee: Long,
            finalPaymentAmount: Long,
            idempotencyKey: String,
        ): OrderGroup = OrderGroup(buyerId, usedPoint, totalProductAmount, totalShippingFee, finalPaymentAmount, idempotencyKey)
    }
}
