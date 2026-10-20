package com.aechak.domain.order.order

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "orders",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_orders_public_id", columnNames = ["public_id"]),
    ],
    indexes = [
        Index(name = "idx_orders_order_group_id", columnList = "order_group_id"),
        Index(name = "idx_orders_seller_id_status", columnList = "seller_id, status, id"),
        Index(name = "idx_orders_status_auto_confirm_due_at", columnList = "status, auto_confirm_due_at"),
        Index(name = "idx_orders_seller_id_purchase_confirmed_at", columnList = "seller_id, purchase_confirmed_at"),
    ],
)
class Order protected constructor(
    orderGroup: OrderGroup,
    sellerId: Long,
    sellerNameSnapshot: String,
    allocatedCouponDiscount: Long,
    sellerShippingFee: Long,
    baseShippingFeeSnapshot: Long?,
    freeShippingThresholdSnapshot: Long?,
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
    var sellerNameSnapshot: String? = sellerNameSnapshot
        protected set

    @Column(nullable = false)
    val allocatedCouponDiscount: Long = allocatedCouponDiscount

    @Column(nullable = false)
    val sellerShippingFee: Long = sellerShippingFee

    /** 주문 시점 셀러 배송 정책 스냅샷 — 부분취소 배송비 재계산의 근거. 이관 전 주문은 NULL */
    @Column
    val baseShippingFeeSnapshot: Long? = baseShippingFeeSnapshot

    @Column
    val freeShippingThresholdSnapshot: Long? = freeShippingThresholdSnapshot

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

    /** 셀러 발주 확인 — 결제완료 건만 상품 준비중으로 간다. */
    fun accept() {
        transitionTo(OrderStatus.PREPARING, OrderErrorCode.ORDER_NOT_ACCEPTABLE)
    }

    /** 상품 준비 완료 — 송장 등록을 기다리는 발송 대기로 간다. */
    fun markDispatchPending() {
        transitionTo(OrderStatus.DISPATCH_PENDING)
    }

    /** 송장 등록에 따른 발송 확정. */
    fun markShipping() {
        transitionTo(OrderStatus.SHIPPING, OrderErrorCode.ORDER_NOT_DISPATCHABLE)
    }

    /** 배송완료 — 구매확정 기한을 함께 부여한다. */
    fun markDelivered(autoConfirmDueAt: LocalDateTime) {
        transitionTo(OrderStatus.DELIVERED)
        this.autoConfirmDueAt = autoConfirmDueAt
    }

    /** 구매확정 기한이 [at] 시점에 지났는지 — 기한이 없는 주문은 자동확정 대상이 아니다. */
    fun isAutoConfirmDue(at: LocalDateTime): Boolean = autoConfirmDueAt?.let { it <= at } == true

    /** 할인 안분액을 뺀 상품금액 합계 — 배송비는 포함하지 않는다. */
    fun productAmountAfterDiscount(): Long =
        _items.sumOf { Math.multiplyExact(it.unitPriceSnapshot, it.quantity.toLong()) - it.discountAllocatedAmount }

    /** 클레임 승인에 따른 취소 — 즉시취소가 막히는 발송 대기 건도 이 경로로 전이한다. */
    fun cancelByClaimApproval() {
        if (status == OrderStatus.PENDING_PAYMENT) {
            throw BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION)
        }
        transitionTo(OrderStatus.CANCELLED)
    }

    private fun transitionTo(
        next: OrderStatus,
        denied: OrderErrorCode = OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION,
    ) {
        if (!status.canTransitionTo(next)) {
            throw BusinessException(denied)
        }
        status = next
    }

    companion object {
        fun create(
            orderGroup: OrderGroup,
            sellerId: Long,
            sellerNameSnapshot: String,
            allocatedCouponDiscount: Long,
            sellerShippingFee: Long,
            baseShippingFeeSnapshot: Long?,
            freeShippingThresholdSnapshot: Long?,
            items: List<OrderItem>,
        ): Order =
            Order(
                orderGroup,
                sellerId,
                sellerNameSnapshot,
                allocatedCouponDiscount,
                sellerShippingFee,
                baseShippingFeeSnapshot,
                freeShippingThresholdSnapshot,
            ).apply {
                items.forEach { _items += it }
            }
    }
}
