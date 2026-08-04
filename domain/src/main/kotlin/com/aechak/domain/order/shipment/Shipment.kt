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
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "shipments")
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

    @Column(length = 50)
    var lastTrackingStatus: String? = null
        protected set

    @Column
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
        fun dispatch(
            courier: CourierMaster,
            order: Order,
            trackingNumber: String,
            shipmentType: ShipmentType,
            claim: Claim? = null,
        ): Shipment =
            Shipment(courier, order, trackingNumber, shipmentType, claim).apply {
                dispatchedAt = LocalDateTime.now()
            }
    }
}
