package com.aechak.domain.order.shipment

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.support.AggregateRoot
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
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "shipments",
    indexes = [
        Index(name = "idx_shipments_order_id", columnList = "order_id"),
        Index(name = "idx_shipments_claim_id", columnList = "claim_id"),
        Index(name = "idx_shipments_dispatched_at", columnList = "dispatched_at, delivered_at"),
    ],
)
class Shipment protected constructor(
    courier: CourierMaster,
    order: Order,
    trackingNumber: String,
    shipmentType: ShipmentType,
    claim: Claim?,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_code", nullable = false)
    val courier: CourierMaster = courier

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order = order

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id")
    val claim: Claim? = claim

    @Column(nullable = false, length = 50)
    val trackingNumber: String = trackingNumber

    @Column
    var dispatchedAt: LocalDateTime? = null
        protected set

    // 추적 갱신만 쓰는 두 컬럼 — 더티체킹 UPDATE에서 빼 둔다. 배송완료 전이도 같은 행을 다시 쓰는데,
    // 거기에 실리면 전이가 읽은 시점의 옛 값(처음에는 null)으로 방금 반영된 추적 결과를 덮는다.
    @Column(length = 50, updatable = false)
    var lastTrackingStatus: String? = null
        protected set

    @Column(updatable = false)
    var lastSyncedAt: LocalDateTime? = null
        protected set

    @Column
    var deliveredAt: LocalDateTime? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val shipmentType: ShipmentType = shipmentType

    fun markDelivered(at: LocalDateTime = LocalDateTime.now()) {
        if (deliveredAt != null) {
            throw BusinessException(OrderErrorCode.SHIPMENT_ALREADY_DELIVERED)
        }
        deliveredAt = at
    }

    companion object {
        /** last_tracking_status 컬럼 길이. 넘치는 문구를 그대로 담으면 저장에서 깨진다 */
        const val MAX_LAST_TRACKING_STATUS_LENGTH = 50

        /**
         * 택배사가 준 상태 문구를 발송에 담을 모양으로 다듬는다. 자유 문자열이라 앞뒤 공백을 떼고
         * 컬럼 길이를 넘는 부분을 자른다. 기록은 리포지토리의 원자 UPDATE가 한다 —
         * 더티체킹으로 쓰면 배송완료 전이가 같은 행을 다시 쓸 때 서로의 컬럼을 지운다.
         */
        fun normalizeTrackingStatus(lastStatus: String): String = lastStatus.trim().take(MAX_LAST_TRACKING_STATUS_LENGTH)

        fun dispatch(
            courier: CourierMaster,
            order: Order,
            trackingNumber: String,
            shipmentType: ShipmentType,
            claim: Claim? = null,
            at: LocalDateTime = LocalDateTime.now(),
        ): Shipment =
            Shipment(courier, order, trackingNumber, shipmentType, claim).apply {
                dispatchedAt = at
            }
    }
}
