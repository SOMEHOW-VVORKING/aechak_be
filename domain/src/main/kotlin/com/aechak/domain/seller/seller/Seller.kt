package com.aechak.domain.seller.seller

import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.error.SellerErrorCode
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
    storeName: String,
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

    @Column(length = 100, nullable = false)
    var storeName: String = storeName
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

    /** 셀러가 기본으로 쓰는 택배사 코드(courier_masters). 미설정이면 null. */
    @Column(length = 30)
    var defaultCourierCode: String? = null
        protected set

    /** true면 반품지 3필드를 쓰지 않고 출고지(zipCode/roadAddress/detailAddress)를 반품지로 본다. */
    @Column(nullable = false)
    var returnSameAsOrigin: Boolean = true
        protected set

    @Column(length = 10)
    var returnZipCode: String? = null
        protected set

    @Column(length = 255)
    var returnRoadAddress: String? = null
        protected set

    @Column(length = 255)
    var returnDetailAddress: String? = null
        protected set

    @Column(nullable = false)
    var jejuExtraFee: Long = 0
        protected set

    @Column(nullable = false)
    var remoteAreaExtraFee: Long = 0
        protected set

    @Column(nullable = false)
    var returnFee: Long = 0
        protected set

    @Column(nullable = false)
    var exchangeFee: Long = 0
        protected set

    /** 발송까지 걸리는 평균 영업일. 허용 범위는 MIN_DISPATCH_DAYS..MAX_DISPATCH_DAYS. */
    @Column(nullable = false)
    var avgDispatchDays: Int = DEFAULT_DISPATCH_DAYS
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

    /**
     * 배송 정책 전체 덮어쓰기. 반품지가 출고지와 같으면 반품지 3필드는 저장하지 않는다.
     * 기본 배송비·무료배송 임계는 구매자 금액 계산이 읽는 값이라 의미를 그대로 둔 채 값만 바꾼다.
     */
    fun updateShippingPolicy(
        defaultCourierCode: String?,
        baseShippingFee: Long,
        freeShippingThreshold: Long?,
        originZipCode: String?,
        originRoadAddress: String?,
        originDetailAddress: String?,
        returnSameAsOrigin: Boolean,
        returnZipCode: String?,
        returnRoadAddress: String?,
        returnDetailAddress: String?,
        jejuExtraFee: Long,
        remoteAreaExtraFee: Long,
        returnFee: Long,
        exchangeFee: Long,
        avgDispatchDays: Int,
        shippingPolicyText: String?,
    ) {
        validateShippingPolicy(
            baseShippingFee = baseShippingFee,
            freeShippingThreshold = freeShippingThreshold,
            jejuExtraFee = jejuExtraFee,
            remoteAreaExtraFee = remoteAreaExtraFee,
            returnFee = returnFee,
            exchangeFee = exchangeFee,
            avgDispatchDays = avgDispatchDays,
        )

        this.defaultCourierCode = defaultCourierCode
        this.baseShippingFee = baseShippingFee
        this.freeShippingThreshold = freeShippingThreshold
        this.zipCode = originZipCode
        this.roadAddress = originRoadAddress
        this.detailAddress = originDetailAddress
        this.returnSameAsOrigin = returnSameAsOrigin
        this.returnZipCode = if (returnSameAsOrigin) null else returnZipCode
        this.returnRoadAddress = if (returnSameAsOrigin) null else returnRoadAddress
        this.returnDetailAddress = if (returnSameAsOrigin) null else returnDetailAddress
        this.jejuExtraFee = jejuExtraFee
        this.remoteAreaExtraFee = remoteAreaExtraFee
        this.returnFee = returnFee
        this.exchangeFee = exchangeFee
        this.avgDispatchDays = avgDispatchDays
        this.shippingPolicyText = shippingPolicyText
    }

    private fun validateShippingPolicy(
        baseShippingFee: Long,
        freeShippingThreshold: Long?,
        jejuExtraFee: Long,
        remoteAreaExtraFee: Long,
        returnFee: Long,
        exchangeFee: Long,
        avgDispatchDays: Int,
    ) {
        val fees = listOf(baseShippingFee, jejuExtraFee, remoteAreaExtraFee, returnFee, exchangeFee)
        if (fees.any { it < 0 }) {
            throw BusinessException(SellerErrorCode.INVALID_SHIPPING_POLICY, detail = "배송 관련 금액은 0원 이상이어야 합니다.")
        }
        if (freeShippingThreshold != null && freeShippingThreshold < 0) {
            throw BusinessException(SellerErrorCode.INVALID_SHIPPING_POLICY, detail = "무료배송 기준 금액은 0원 이상이어야 합니다.")
        }
        // 임계가 기본 배송비보다 낮으면 "임계를 넘겨도 배송비가 더 비싼" 자기모순이 된다
        if (freeShippingThreshold != null && freeShippingThreshold < baseShippingFee) {
            throw BusinessException(
                SellerErrorCode.INVALID_SHIPPING_POLICY,
                detail = "무료배송 기준 금액은 기본 배송비보다 낮을 수 없습니다.",
            )
        }
        if (avgDispatchDays !in MIN_DISPATCH_DAYS..MAX_DISPATCH_DAYS) {
            throw BusinessException(
                SellerErrorCode.INVALID_SHIPPING_POLICY,
                detail = "평균 발송 소요일은 ${MIN_DISPATCH_DAYS}일 이상 ${MAX_DISPATCH_DAYS}일 이하여야 합니다.",
            )
        }
    }

    companion object {
        /** MySQL 중복 키 메시지의 PK 식별자 — 같은 신청 동시 승인의 개점 INSERT 충돌 판별용. */
        const val PK_CONFLICT_MARKER = "sellers.PRIMARY"

        const val MIN_DISPATCH_DAYS = 1
        const val MAX_DISPATCH_DAYS = 7
        const val DEFAULT_DISPATCH_DAYS = 2

        fun open(
            userId: Long,
            storeName: String,
            baseShippingFee: Long,
        ): Seller = Seller(userId, baseShippingFee, storeName)
    }
}
