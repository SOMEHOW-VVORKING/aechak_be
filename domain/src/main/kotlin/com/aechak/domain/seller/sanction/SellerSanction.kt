package com.aechak.domain.seller.sanction

import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.sanction.enums.SellerSanctionAction
import com.aechak.domain.seller.sanction.enums.SellerSanctionReasonCode
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 판매정지 이력 — 누가 언제 왜 어떤 조치를 했는지의 추가 전용 기록.
 * 분쟁에서 쓸 수 있어야 하므로 행은 수정하지 않는다(해제도 UPDATE가 아니라 RELEASE 행 추가).
 *
 * 대상을 연관이 아니라 id로 들고 있는 이유: 이력 한 건마다 셀러 애그리거트(배송 정책 TEXT·이미지 키·
 * 주소)를 끌고 올 이유가 없고, 조회는 운영 화면 전용 읽기 포트가 따로 담당한다.
 * 행위자도 같은 이유로 id다(UserSanction과 같은 판단).
 */
@Entity
@Table(
    name = "seller_sanctions",
    indexes = [Index(name = "ix_seller_sanctions_seller_id", columnList = "seller_id, id")],
)
class SellerSanction protected constructor(
    sellerId: Long,
    action: SellerSanctionAction,
    reasonCode: SellerSanctionReasonCode?,
    reasonDetail: String?,
    effectiveUntil: LocalDateTime?,
    memo: String?,
    actedByAdminId: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    /** sellers.user_id — sellers는 user_id가 PK이고 별도 셀러 식별자가 없다. */
    @Column(name = "seller_id", nullable = false)
    val sellerId: Long = sellerId

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    val action: SellerSanctionAction = action

    /** 해제 행은 제재 어휘를 쓰지 않으므로 비어 있다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    val reasonCode: SellerSanctionReasonCode? = reasonCode

    @Column(length = REASON_DETAIL_MAX_LENGTH)
    val reasonDetail: String? = reasonDetail

    /** 그때 정한 해제 예정 시각. 무기한 정지와 해제 행에는 값이 없다. */
    @Column
    val effectiveUntil: LocalDateTime? = effectiveUntil

    /** 조치 시점의 운영자 메모 사본 — 셀러에게 보이지 않는다. 현재 메모는 sellers.admin_memo다. */
    @Column(length = MEMO_MAX_LENGTH)
    val memo: String? = memo

    @Column(nullable = false)
    val actedByAdminId: Long = actedByAdminId

    companion object {
        const val REASON_DETAIL_MAX_LENGTH = 500

        /** sellers.admin_memo와 같은 문자열을 사본으로 받으므로 길이를 같이 둔다. */
        const val MEMO_MAX_LENGTH = 500

        /** 판매정지 기록 — 기타 사유는 상세 사유가 필수다. 기간은 없으면 무기한 정지다. */
        fun suspend(
            sellerId: Long,
            reasonCode: SellerSanctionReasonCode,
            reasonDetail: String?,
            effectiveUntil: LocalDateTime?,
            memo: String?,
            actedByAdminId: Long,
        ): SellerSanction {
            if (reasonCode == SellerSanctionReasonCode.OTHER && reasonDetail.isNullOrBlank()) {
                throw BusinessException(SellerErrorCode.SELLER_SANCTION_REASON_DETAIL_REQUIRED)
            }
            return SellerSanction(
                sellerId = sellerId,
                action = SellerSanctionAction.SUSPEND,
                reasonCode = reasonCode,
                reasonDetail = normalize(reasonDetail, REASON_DETAIL_MAX_LENGTH),
                effectiveUntil = effectiveUntil,
                memo = normalize(memo, MEMO_MAX_LENGTH),
                actedByAdminId = actedByAdminId,
            )
        }

        /** 해제 기록 — 사유는 고정 어휘가 아니라 상세 문장만 받는다. */
        fun release(
            sellerId: Long,
            reasonDetail: String?,
            memo: String?,
            actedByAdminId: Long,
        ): SellerSanction =
            SellerSanction(
                sellerId = sellerId,
                action = SellerSanctionAction.RELEASE,
                reasonCode = null,
                reasonDetail = normalize(reasonDetail, REASON_DETAIL_MAX_LENGTH),
                effectiveUntil = null,
                memo = normalize(memo, MEMO_MAX_LENGTH),
                actedByAdminId = actedByAdminId,
            )

        private fun normalize(
            text: String?,
            maxLength: Int,
        ): String? {
            val trimmed = text?.trim()
            if (trimmed.isNullOrEmpty()) return null
            if (trimmed.length > maxLength) throw BusinessException(SellerErrorCode.SELLER_SANCTION_TEXT_TOO_LONG)
            return trimmed
        }
    }
}
