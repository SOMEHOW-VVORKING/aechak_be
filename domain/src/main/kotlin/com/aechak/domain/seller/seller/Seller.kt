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

    @Column(length = SUSPENSION_REASON_MAX_LENGTH)
    var suspensionReason: String? = null
        protected set

    /**
     * 현재 판매정지의 해제 예정 시각. 무기한 정지는 null이다.
     * 이 값이 지나도 상태는 저절로 풀리지 않는다 — 노출·쓰기 게이트는 status만 보고, 해제는 운영자 조치다.
     */
    @Column
    var suspensionUntil: LocalDateTime? = null
        protected set

    @Column(nullable = false)
    var statusChangedAt: LocalDateTime = LocalDateTime.now()
        protected set

    /** 셀러별 운영자 내부 메모 한 칸. 셀러에게 노출하지 않는다. */
    @Column(length = ADMIN_MEMO_MAX_LENGTH)
    var adminMemo: String? = null
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
     * 스토어 설정 전체 덮어쓰기. 주소 3필드는 배송 정책의 출고지와 같은 컬럼이라 두 화면이 같은 값을 본다.
     * 이미지 키는 승격이 끝난 저장 키만 들어온다(승격은 트랜잭션 밖 호출부 책임).
     * 상호는 앞뒤 공백을 떼고 저장하며, 떼고 나면 비는 값은 거절한다.
     */
    fun updateStore(
        storeName: String,
        brandIntro: String?,
        profileImageKey: String?,
        bannerImageKey: String?,
        zipCode: String?,
        roadAddress: String?,
        detailAddress: String?,
    ) {
        val normalizedStoreName = storeName.trim()
        validateStoreName(normalizedStoreName)

        this.storeName = normalizedStoreName
        this.brandIntro = brandIntro
        this.profileImageKey = profileImageKey
        this.bannerImageKey = bannerImageKey
        this.zipCode = zipCode
        this.roadAddress = roadAddress
        this.detailAddress = detailAddress
    }

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

    /**
     * 판매정지 — 지금 상태를 previousStatus에 보관하고 SUSPENDED로 옮긴다.
     * 셀러센터 쓰기와 노출 조회의 게이트가 전부 `status != ACTIVE`를 보고 있어 이 전이 하나로 효과가 전역에 든다.
     * 상품의 판매 상태(products.sale_status)는 건드리지 않는다 — 일괄로 바꾸면 해제할 때 원래 값을 복원할 수 없다.
     *
     * @param reason 사람이 읽는 사유 한 줄. 해제하면 비운다
     * @param until 해제 예정 시각. null이면 무기한 정지(자동 해제 경로는 없다)
     */
    fun suspend(
        reason: String?,
        until: LocalDateTime?,
    ) {
        if (status == SellerStatus.SUSPENDED) {
            throw BusinessException(SellerErrorCode.SELLER_ALREADY_SUSPENDED)
        }
        // 탈퇴·탈퇴 요청은 되돌릴 상태로 보관할 수 없다 — 해제가 탈퇴한 셀러를 영업 상태로 살려낸다
        if (status !in SUSPENDABLE_STATUSES) {
            throw BusinessException(SellerErrorCode.SELLER_STATUS_TRANSITION_NOT_ALLOWED)
        }
        previousStatus = status
        status = SellerStatus.SUSPENDED
        // 넘치면 자른다. 호출부가 넘기는 값은 어휘 라벨에 상세 사유를 이어 붙인 것이어서
        // (SellerSanctionReasonCode.describe) 상세가 상한을 채우면 라벨과 구분자만큼 컬럼을
        // 넘고, 그대로 두면 MySQL strict 모드가 Data too long 으로 전이 트랜잭션을 되돌린다.
        // 메모(updateAdminMemo)와 달리 거부하지 않는 이유가 둘이다. 입구가 상세 사유를
        // REASON_DETAIL_MAX_LENGTH 까지 받아 주는데 여기서 막으면 운영자가 통과하는 길이를
        // 알 수 없고(라벨 길이만큼 어휘마다 다르다), 이 칸은 어드민 목록·상세가 읽는 표시용
        // 사본이라 원문이 seller_sanctions.reason_detail 에 그대로 남는다.
        suspensionReason = reason?.take(SUSPENSION_REASON_MAX_LENGTH)
        suspensionUntil = until
        statusChangedAt = LocalDateTime.now()
    }

    /**
     * 판매정지 해제 — 정지 직전 상태로 되돌린다.
     * previousStatus가 비어 있는 행은 이 전이가 생기기 전에 정지된 셀러라 ACTIVE로 되돌린다.
     */
    fun releaseSuspension() {
        if (status != SellerStatus.SUSPENDED) {
            throw BusinessException(SellerErrorCode.SELLER_NOT_SUSPENDED)
        }
        status = previousStatus ?: SellerStatus.ACTIVE
        previousStatus = null
        suspensionReason = null
        suspensionUntil = null
        statusChangedAt = LocalDateTime.now()
    }

    /** 운영자 내부 메모 덮어쓰기. 빈 값은 메모를 지운 것으로 본다. */
    fun updateAdminMemo(memo: String?) {
        val trimmed = memo?.trim()
        if (trimmed != null && trimmed.length > ADMIN_MEMO_MAX_LENGTH) {
            throw BusinessException(SellerErrorCode.SELLER_SANCTION_TEXT_TOO_LONG)
        }
        adminMemo = trimmed?.takeIf { it.isNotEmpty() }
    }

    /** 상호는 구매자에게 보이는 이름이라 빈 값·컬럼 초과를 엔티티가 막는다. */
    private fun validateStoreName(storeName: String) {
        if (storeName.isEmpty()) {
            throw BusinessException(SellerErrorCode.INVALID_STORE_SETTINGS, detail = "상호(스토어명)는 필수입니다.")
        }
        if (storeName.length > MAX_STORE_NAME_LENGTH) {
            throw BusinessException(
                SellerErrorCode.INVALID_STORE_SETTINGS,
                detail = "상호(스토어명)는 ${MAX_STORE_NAME_LENGTH}자 이하여야 합니다.",
            )
        }
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
        // 상한이 없으면 셀러별 배송비를 합산하는 주문 금액 계산이 Long 범위를 넘겨 작은 금액으로 되돌아간다
        if (fees.any { it > MAX_SHIPPING_FEE }) {
            throw BusinessException(
                SellerErrorCode.INVALID_SHIPPING_POLICY,
                detail = "배송 관련 금액은 ${MAX_SHIPPING_FEE}원 이하여야 합니다.",
            )
        }
        if (freeShippingThreshold != null && freeShippingThreshold < 0) {
            throw BusinessException(SellerErrorCode.INVALID_SHIPPING_POLICY, detail = "무료배송 기준 금액은 0원 이상이어야 합니다.")
        }
        if (freeShippingThreshold != null && freeShippingThreshold > MAX_FREE_SHIPPING_THRESHOLD) {
            throw BusinessException(
                SellerErrorCode.INVALID_SHIPPING_POLICY,
                detail = "무료배송 기준 금액은 ${MAX_FREE_SHIPPING_THRESHOLD}원 이하여야 합니다.",
            )
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

        /** store_name 컬럼 길이. 요청 DTO의 상한이 이 상수를 참조한다. */
        const val MAX_STORE_NAME_LENGTH = 100

        /** brand_intro 컬럼 길이. */
        const val MAX_BRAND_INTRO_LENGTH = 1000

        /** suspension_reason 컬럼 길이. */
        const val SUSPENSION_REASON_MAX_LENGTH = 500

        /** admin_memo 컬럼 길이. 요청 DTO의 상한이 이 상수를 참조한다. */
        const val ADMIN_MEMO_MAX_LENGTH = 500

        /** 판매정지를 걸 수 있는 상태 — 해제가 되돌릴 자리가 있는 상태뿐이다. */
        private val SUSPENDABLE_STATUSES = setOf(SellerStatus.ACTIVE, SellerStatus.PAUSED)

        /** profile_image_key·banner_image_key 컬럼 길이. */
        const val MAX_IMAGE_KEY_LENGTH = 1024

        /** shipping_policy_text가 TEXT 컬럼이라 바이트 한도를 넘지 않게 글자 수로 먼저 막는다. */
        const val MAX_SHIPPING_POLICY_TEXT_LENGTH = 2000

        /** 기본 배송비·지역 추가비·반품비·교환비의 상한. */
        const val MAX_SHIPPING_FEE = 1_000_000L

        /** 무료배송 기준 금액의 상한. */
        const val MAX_FREE_SHIPPING_THRESHOLD = 100_000_000L

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
