package com.aechak.domain.user.sanction

import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.sanction.enums.SanctionAction
import com.aechak.domain.user.sanction.enums.SanctionReasonCode
import com.aechak.domain.user.user.User
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 계약 — 이력 행은 "분쟁에서 쓸 수 있는 기록"이어야 한다.
 * 기간 없는 임시 정지나 상세 없는 기타 사유가 저장되면 나중에 아무것도 설명할 수 없다.
 */
class UserSanctionTest {
    private fun target(): User = User.preRegister().apply { completeOnboarding("코코집사") }

    @Test
    fun `임시 정지는 기간이 없으면 30801로 거부된다`() {
        val exception =
            assertFailsWith<BusinessException> {
                UserSanction.sanction(
                    targetUser = target(),
                    action = SanctionAction.SUSPEND,
                    reasonCode = SanctionReasonCode.ABUSIVE_REVIEW,
                    reasonDetail = null,
                    effectiveUntil = null,
                    memo = null,
                    actedByAdminId = 1L,
                )
            }

        assertEquals(UserErrorCode.SANCTION_PERIOD_REQUIRED, exception.errorCode)
    }

    @Test
    fun `임시 정지가 아닌 조치에 기간을 붙이면 30802로 거부된다`() {
        for (action in listOf(SanctionAction.RESTRICT, SanctionAction.BAN, SanctionAction.FORCE_WITHDRAW)) {
            val exception =
                assertFailsWith<BusinessException> {
                    UserSanction.sanction(
                        targetUser = target(),
                        action = action,
                        reasonCode = SanctionReasonCode.FALSE_REPORT,
                        reasonDetail = null,
                        effectiveUntil = LocalDateTime.now().plusDays(7),
                        memo = null,
                        actedByAdminId = 1L,
                    )
                }

            assertEquals(UserErrorCode.SANCTION_PERIOD_NOT_ALLOWED, exception.errorCode, "조치=$action")
        }
    }

    @Test
    fun `기타 사유는 상세 사유가 없으면 30800으로 거부된다`() {
        for (blank in listOf(null, "", "   ")) {
            val exception =
                assertFailsWith<BusinessException> {
                    UserSanction.sanction(
                        targetUser = target(),
                        action = SanctionAction.RESTRICT,
                        reasonCode = SanctionReasonCode.OTHER,
                        reasonDetail = blank,
                        effectiveUntil = null,
                        memo = null,
                        actedByAdminId = 1L,
                    )
                }

            assertEquals(UserErrorCode.SANCTION_REASON_DETAIL_REQUIRED, exception.errorCode)
        }
    }

    @Test
    fun `고정 어휘 사유는 상세 없이도 기록된다`() {
        val sanction =
            UserSanction.sanction(
                targetUser = target(),
                action = SanctionAction.RESTRICT,
                reasonCode = SanctionReasonCode.ABUSIVE_REVIEW,
                reasonDetail = null,
                effectiveUntil = null,
                memo = "  3회 경고 후 조치  ",
                actedByAdminId = 42L,
            )

        assertEquals(SanctionAction.RESTRICT, sanction.action)
        assertEquals(SanctionReasonCode.ABUSIVE_REVIEW, sanction.reasonCode)
        assertNull(sanction.reasonDetail)
        assertEquals("3회 경고 후 조치", sanction.memo, "메모는 앞뒤 공백을 떼고 저장한다")
        assertEquals(42L, sanction.actedByAdminId)
    }

    @Test
    fun `상한을 넘는 상세 사유와 메모는 30803으로 거부된다`() {
        val tooLongDetail = "가".repeat(UserSanction.REASON_DETAIL_MAX_LENGTH + 1)
        val tooLongMemo = "가".repeat(UserSanction.MEMO_MAX_LENGTH + 1)

        assertEquals(
            UserErrorCode.SANCTION_TEXT_TOO_LONG,
            assertFailsWith<BusinessException> { sanctionWith(reasonDetail = tooLongDetail) }.errorCode,
        )
        assertEquals(
            UserErrorCode.SANCTION_TEXT_TOO_LONG,
            assertFailsWith<BusinessException> { sanctionWith(memo = tooLongMemo) }.errorCode,
        )
    }

    @Test
    fun `해제 행은 제재 사유 어휘를 쓰지 않는다`() {
        val release = UserSanction.release(target(), "오인 제재 확인", "CS 티켓 1024", 7L)

        assertEquals(SanctionAction.RELEASE, release.action)
        assertNull(release.reasonCode, "해제는 제재 어휘를 쓰지 않는다")
        assertEquals("오인 제재 확인", release.reasonDetail)
        assertNull(release.effectiveUntil)
        assertEquals(7L, release.actedByAdminId)
    }

    @Test
    fun `해제를 제재 팩토리로 기록할 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            UserSanction.sanction(
                targetUser = target(),
                action = SanctionAction.RELEASE,
                reasonCode = SanctionReasonCode.ABUSIVE_REVIEW,
                reasonDetail = null,
                effectiveUntil = null,
                memo = null,
                actedByAdminId = 1L,
            )
        }
    }

    @Test
    fun `기간을 요구하는 조치는 임시 정지뿐이다`() {
        assertEquals(
            listOf(SanctionAction.SUSPEND),
            SanctionAction.entries.filter { it.requiresPeriod() },
        )
    }

    private fun sanctionWith(
        reasonDetail: String? = null,
        memo: String? = null,
    ): UserSanction =
        UserSanction.sanction(
            targetUser = target(),
            action = SanctionAction.RESTRICT,
            reasonCode = SanctionReasonCode.ABUSIVE_REVIEW,
            reasonDetail = reasonDetail,
            effectiveUntil = null,
            memo = memo,
            actedByAdminId = 1L,
        )
}
