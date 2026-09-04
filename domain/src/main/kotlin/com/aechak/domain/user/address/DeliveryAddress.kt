package com.aechak.domain.user.address

import com.aechak.domain.support.BaseEntity
import com.aechak.domain.user.address.enums.DeliveryAddressStatus
import com.aechak.domain.user.user.User
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

@Entity
@Table(name = "delivery_addresses")
class DeliveryAddress protected constructor(
    user: User,
    receiverName: String,
    contactNumber: ByteArray,
    zipCode: String,
    baseAddress: String,
    detailAddress: String?,
    deliveryMemo: String?,
    label: String?,
    isDefault: Boolean,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User = user

    @Column(length = 255, nullable = false)
    var receiverName: String = receiverName
        protected set

    @Column(length = 255, nullable = false)
    var contactNumber: ByteArray = contactNumber
        protected set

    @Column(length = 10, nullable = false)
    var zipCode: String = zipCode
        protected set

    @Column(length = 512, nullable = false)
    var baseAddress: String = baseAddress
        protected set

    @Column(length = 512)
    var detailAddress: String? = detailAddress
        protected set

    @Column(length = 255)
    var deliveryMemo: String? = deliveryMemo
        protected set

    @Column(length = 100)
    var label: String? = label
        protected set

    @Column(nullable = false)
    var isDefault: Boolean = isDefault
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var status: DeliveryAddressStatus = DeliveryAddressStatus.ACTIVE
        protected set

    /** 필드 전체 갈아끼움. 소유권 검증·기본 처리는 서비스 몫. */
    fun update(
        receiverName: String,
        contactNumber: ByteArray,
        zipCode: String,
        baseAddress: String,
        detailAddress: String?,
        deliveryMemo: String?,
        label: String?,
    ) {
        this.receiverName = receiverName
        this.contactNumber = contactNumber
        this.zipCode = zipCode
        this.baseAddress = baseAddress
        this.detailAddress = detailAddress
        this.deliveryMemo = deliveryMemo
        this.label = label
    }

    fun markAsDefault() {
        isDefault = true
    }

    fun releaseDefault() {
        isDefault = false
    }

    fun delete() {
        status = DeliveryAddressStatus.DELETED
        isDefault = false // 남겨두면 복원 시 기본이 2개될 수 있음
    }

    companion object {
        /** 수령인명 평문 상한(로직 제한). 법정 최장 이름·병기 관례를 덮고, 주문 스냅샷 암호문이 컬럼(255자)에 들어가는 상한이기도 하다 */
        const val RECEIVER_NAME_MAX_LENGTH = 50

        fun register(
            user: User,
            receiverName: String,
            contactNumber: ByteArray,
            zipCode: String,
            baseAddress: String,
            detailAddress: String? = null,
            deliveryMemo: String? = null,
            label: String? = null,
            isDefault: Boolean = false,
        ): DeliveryAddress =
            DeliveryAddress(
                user,
                receiverName,
                contactNumber,
                zipCode,
                baseAddress,
                detailAddress,
                deliveryMemo,
                label,
                isDefault,
            )
    }
}
