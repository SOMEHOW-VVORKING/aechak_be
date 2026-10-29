package com.aechak.domain.user.sanction

import com.aechak.common.error.BusinessException
import com.aechak.domain.support.BaseEntity
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.sanction.enums.SanctionAction
import com.aechak.domain.user.sanction.enums.SanctionReasonCode
import com.aechak.domain.user.user.User
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

/**
 * 제재 이력 — 누가 언제 왜 어떤 조치를 했는지의 추가 전용 기록.
 * 분쟁에서 쓸 수 있어야 하므로 행은 수정하지 않는다(해제도 UPDATE가 아니라 RELEASE 행 추가).
 *
 * 행위자를 연관이 아니라 id 로 들고 있는 이유: 감사 기록에 필요한 것은 "누가"의 식별자뿐이고,
 * User 를 연관으로 걸면 제재 한 건마다 운영자 애그리거트(프로필 포함)를 끌고 온다.
 */
@Entity
@Table(
    name = "user_sanctions",
    indexes = [Index(name = "ix_user_sanctions_target_user_id", columnList = "target_user_id, id")],
)
class UserSanction protected constructor(
    targetUser: User,
    action: SanctionAction,
    reasonCode: SanctionReasonCode?,
    reasonDetail: String?,
    effectiveUntil: LocalDateTime?,
    memo: String?,
    actedByAdminId: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    val targetUser: User = targetUser

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    val action: SanctionAction = action

    /** 해제 행은 제재 어휘를 쓰지 않으므로 비어 있다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    val reasonCode: SanctionReasonCode? = reasonCode

    @Column(length = 500)
    val reasonDetail: String? = reasonDetail

    /** 임시 정지의 만료 시각. 다른 조치에는 값이 없다. */
    @Column
    val effectiveUntil: LocalDateTime? = effectiveUntil

    /** 운영자 내부 공유용 메모 — 회원에게 보이지 않는다. */
    @Column(length = 300)
    val memo: String? = memo

    @Column(nullable = false)
    val actedByAdminId: Long = actedByAdminId

    companion object {
        const val REASON_DETAIL_MAX_LENGTH = 500
        const val MEMO_MAX_LENGTH = 300

        /** 제재 기록 — 기간은 임시 정지에만, 상세 사유는 기타 사유에 필수다. */
        fun sanction(
            targetUser: User,
            action: SanctionAction,
            reasonCode: SanctionReasonCode,
            reasonDetail: String?,
            effectiveUntil: LocalDateTime?,
            memo: String?,
            actedByAdminId: Long,
        ): UserSanction {
            require(action != SanctionAction.RELEASE) { "해제는 release()로 기록한다." }
            if (action.requiresPeriod() && effectiveUntil == null) {
                throw BusinessException(UserErrorCode.SANCTION_PERIOD_REQUIRED)
            }
            if (!action.requiresPeriod() && effectiveUntil != null) {
                throw BusinessException(UserErrorCode.SANCTION_PERIOD_NOT_ALLOWED)
            }
            if (reasonCode == SanctionReasonCode.OTHER && reasonDetail.isNullOrBlank()) {
                throw BusinessException(UserErrorCode.SANCTION_REASON_DETAIL_REQUIRED)
            }
            return UserSanction(
                targetUser = targetUser,
                action = action,
                reasonCode = reasonCode,
                reasonDetail = normalize(reasonDetail, REASON_DETAIL_MAX_LENGTH),
                effectiveUntil = effectiveUntil,
                memo = normalize(memo, MEMO_MAX_LENGTH),
                actedByAdminId = actedByAdminId,
            )
        }

        /** 해제 기록 — 사유는 고정 어휘가 아니라 상세 문장만 받는다. */
        fun release(
            targetUser: User,
            reasonDetail: String?,
            memo: String?,
            actedByAdminId: Long,
        ): UserSanction =
            UserSanction(
                targetUser = targetUser,
                action = SanctionAction.RELEASE,
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
            if (trimmed.length > maxLength) throw BusinessException(UserErrorCode.SANCTION_TEXT_TOO_LONG)
            return trimmed
        }
    }
}
