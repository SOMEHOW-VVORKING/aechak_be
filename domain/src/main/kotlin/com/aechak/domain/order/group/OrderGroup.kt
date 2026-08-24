package com.aechak.domain.order.group

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "order_groups",
    indexes = [
        Index(name = "idx_order_groups_buyer_id", columnList = "buyer_id, id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_order_group_public_id", columnNames = ["public_id"]),
        UniqueConstraint(name = "uk_order_group_idempotency_key", columnNames = ["idempotency_key"]),
    ],
)
class OrderGroup protected constructor(
    buyerId: Long,
    deliveryAddressId: Long,
    deliveryAddress: DeliveryAddressSnapshot,
    usedPoint: Long,
    totalProductAmount: Long,
    totalShippingFee: Long,
    finalPaymentAmount: Long,
    idempotencyKey: String,
    expiresAt: LocalDateTime,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false, length = 26)
    val publicId: String = Ulid.generate()

    @Column(nullable = false)
    val buyerId: Long = buyerId

    @Column
    val deliveryAddressId: Long? = deliveryAddressId

    @Embedded
    val deliveryAddress: DeliveryAddressSnapshot = deliveryAddress

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
    val expiresAt: LocalDateTime? = expiresAt

    @Column(name = "idempotency_key", nullable = false, length = IDEMPOTENCY_KEY_MAX_LENGTH)
    val idempotencyKey: String = idempotencyKey

    fun isExpired(now: LocalDateTime = LocalDateTime.now()): Boolean = expiresAt?.isBefore(now) ?: false

    /** 웹훅 재전달을 통과시키려고 이미 PAID면 아무것도 안 함. 취소된 그룹에 온 결제는 환불이 필요한 사건이라 갈라서 막음 */
    fun markPaid() {
        if (status == OrderGroupStatus.PAID) return
        transitionFromPending(OrderGroupStatus.PAID)
    }

    /** 만료 배치 재실행을 통과시키려고 이미 CANCELLED면 아무것도 안 함 */
    fun cancelUnpaid() {
        if (status == OrderGroupStatus.CANCELLED) return
        transitionFromPending(OrderGroupStatus.CANCELLED)
    }

    /** 상태 규칙만 강제함. 동시 선점은 저장소 조건부 UPDATE가 가름 */
    private fun transitionFromPending(next: OrderGroupStatus) {
        if (status != OrderGroupStatus.PENDING_PAYMENT) {
            throw BusinessException(OrderErrorCode.INVALID_ORDER_GROUP_STATUS_TRANSITION)
        }
        status = next
    }

    companion object {
        const val IDEMPOTENCY_KEY_MAX_LENGTH = 100

        fun create(
            buyerId: Long,
            deliveryAddressId: Long,
            deliveryAddress: DeliveryAddressSnapshot,
            usedPoint: Long,
            totalProductAmount: Long,
            totalShippingFee: Long,
            idempotencyKey: String,
            expiresAt: LocalDateTime,
        ): OrderGroup {
            if (totalProductAmount < 0 || totalShippingFee < 0 || usedPoint < 0) {
                throw BusinessException(OrderErrorCode.INVALID_ORDER_GROUP_AMOUNT)
            }
            val payableAmount = totalProductAmount + totalShippingFee // 쿠폰이 들어오면 couponDiscountAmount를 여기서 뺌
            if (usedPoint > payableAmount) {
                throw BusinessException(OrderErrorCode.POINT_EXCEEDS_PAYABLE_AMOUNT)
            }
            val finalPaymentAmount = payableAmount - usedPoint
            return OrderGroup(
                buyerId = buyerId,
                deliveryAddressId = deliveryAddressId,
                deliveryAddress = deliveryAddress,
                usedPoint = usedPoint,
                totalProductAmount = totalProductAmount,
                totalShippingFee = totalShippingFee,
                finalPaymentAmount = finalPaymentAmount,
                idempotencyKey = idempotencyKey,
                expiresAt = expiresAt,
            )
        }
    }
}
