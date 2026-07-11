package com.aechak.domain.user.address

import com.aechak.domain.support.BaseEntity
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
import com.aechak.domain.user.address.enums.DeliveryAddressStatus
import com.aechak.domain.user.user.User

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

    @Column(nullable = false)
    var isDefault: Boolean = isDefault
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var status: DeliveryAddressStatus = DeliveryAddressStatus.ACTIVE
        protected set

    fun delete() {
        status = DeliveryAddressStatus.DELETED
    }


    companion object {
        fun register(
            user: User,
            receiverName: String,
            contactNumber: ByteArray,
            zipCode: String,
            baseAddress: String,
            detailAddress: String? = null,
            deliveryMemo: String? = null,
            isDefault: Boolean = false,
        ): DeliveryAddress = DeliveryAddress(
            user, receiverName, contactNumber, zipCode, baseAddress, detailAddress, deliveryMemo, isDefault,
        )
    }
}
