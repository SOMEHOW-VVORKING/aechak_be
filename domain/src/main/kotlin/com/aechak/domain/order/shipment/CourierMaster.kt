package com.aechak.domain.order.shipment

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "courier_masters")
class CourierMaster protected constructor(
    courierCode: String,
    courierName: String,
    isActive: Boolean,
    trackingFormatPattern: String?,
) : AggregateRoot() {
    @Id
    @Column(name = "courier_code", length = 30)
    val courierCode: String = courierCode

    @Column(nullable = false, length = 60)
    var courierName: String = courierName
        protected set

    @Column(length = 255)
    var trackingFormatPattern: String? = trackingFormatPattern
        protected set

    @Column(nullable = false)
    var isActive: Boolean = isActive
        protected set

    companion object {
        fun register(
            courierCode: String,
            courierName: String,
            isActive: Boolean = true,
            trackingFormatPattern: String? = null,
        ): CourierMaster = CourierMaster(courierCode, courierName, isActive, trackingFormatPattern)
    }
}
