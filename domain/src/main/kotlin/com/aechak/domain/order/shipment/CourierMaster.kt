package com.aechak.domain.order.shipment

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.shipment.enums.TrackingNumberCheck
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

    /**
     * 송장번호가 이 택배사의 형식을 만족하는지 본다. 패턴이 없으면 검증하지 않는다.
     * 패턴이 정규식으로 컴파일되지 않으면 검증을 건너뛴다 — 마스터 데이터 오류가 송장 등록을 막지 않게 한다.
     * 건너뛴 사유는 반환값으로 알린다. 호출자가 경고를 남긴다.
     */
    fun validateTrackingNumber(trackingNumber: String): TrackingNumberCheck {
        val pattern = trackingFormatPattern ?: return TrackingNumberCheck.SKIPPED_NO_PATTERN
        val regex = runCatching { Regex(pattern) }.getOrNull() ?: return TrackingNumberCheck.SKIPPED_BROKEN_PATTERN
        if (!regex.matches(trackingNumber)) {
            throw BusinessException(OrderErrorCode.INVALID_TRACKING_NUMBER)
        }
        return TrackingNumberCheck.MATCHED
    }

    companion object {
        fun register(
            courierCode: String,
            courierName: String,
            isActive: Boolean = true,
            trackingFormatPattern: String? = null,
        ): CourierMaster = CourierMaster(courierCode, courierName, isActive, trackingFormatPattern)
    }
}
