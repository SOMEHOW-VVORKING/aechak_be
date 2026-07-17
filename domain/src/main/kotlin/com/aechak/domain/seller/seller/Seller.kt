package com.aechak.domain.seller.seller

import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.seller.seller.enums.TelesalesNoticeStatus
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(name = "sellers")
class Seller protected constructor(
    userId: Long,
    baseShippingFee: Long,
) : AggregateRoot() {
    @Id
    val userId: Long = userId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: SellerStatus = SellerStatus.ACTIVE
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var previousStatus: SellerStatus? = null
        protected set

    @Column(length = 500)
    var suspensionReason: String? = null
        protected set

    @Column(nullable = false)
    var statusChangedAt: LocalDateTime = LocalDateTime.now()
        protected set

    @Column(length = 1000)
    var brandIntro: String? = null
        protected set

    @Column(length = 1024)
    var profileImageKey: String? = null
        protected set

    @Column(length = 1024)
    var bannerImageKey: String? = null
        protected set

    @Column(length = 10)
    var zipCode: String? = null
        protected set

    @Column(length = 255)
    var roadAddress: String? = null
        protected set

    @Column(length = 255)
    var detailAddress: String? = null
        protected set

    @Column(columnDefinition = "TEXT")
    var shippingPolicyText: String? = null
        protected set

    @Column(nullable = false)
    var baseShippingFee: Long = baseShippingFee
        protected set

    var freeShippingThreshold: Long? = null
        protected set

    /** 저장소 원자 UPDATE로만 증가 — 엔티티 증가 금지(동시성). */
    @Column(nullable = false)
    var confirmedOrderCount: Int = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var telesalesNoticeStatus: TelesalesNoticeStatus = TelesalesNoticeStatus.NOT_REQUIRED
        protected set

    var telesalesNoticeSentAt: LocalDateTime? = null
        protected set

    @Version
    var version: Long = 0
        protected set

    companion object {
        fun open(
            userId: Long,
            baseShippingFee: Long,
        ): Seller = Seller(userId, baseShippingFee)
    }
}
