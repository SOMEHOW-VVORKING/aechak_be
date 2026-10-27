package com.aechak.domain.user.user

import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.user.enums.UserStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 계약 — 제재는 전용 메서드로만 움직이고, 종단 상태(탈퇴·영구 정지)는 더 움직이지 않는다.
 * 깨지면 운영자가 되돌릴 수 없다고 안내한 조치가 조용히 되돌아가거나, 탈퇴한 계정이 다시 제재 대상이 된다.
 */
class UserSanctionTransitionTest {
    private fun activeUser(): User = User.preRegister().apply { completeOnboarding("코코집사") }

    private fun future(): LocalDateTime = LocalDateTime.now().plusDays(7)

    @Test
    fun `restrict는 상태를 RESTRICTED로 바꾸고 만료 시각을 비운다`() {
        val user = activeUser()
        user.suspendUntil(future())

        user.restrict()

        assertEquals(UserStatus.RESTRICTED, user.status)
        assertNull(user.sanctionUntil, "이용 제한은 기간이 없는 제재다")
    }

    @Test
    fun `suspendUntil은 상태와 만료 시각을 함께 세팅한다`() {
        val user = activeUser()
        val until = future()

        user.suspendUntil(until)

        assertEquals(UserStatus.SUSPENDED, user.status)
        assertEquals(until, user.sanctionUntil)
    }

    @Test
    fun `과거 시각으로는 임시 정지할 수 없다 — 걸자마자 풀린 정지가 되기 때문`() {
        val user = activeUser()

        val exception = assertFailsWith<BusinessException> { user.suspendUntil(LocalDateTime.now().minusMinutes(1)) }

        assertEquals(UserErrorCode.SANCTION_PERIOD_NOT_IN_FUTURE, exception.errorCode)
        assertEquals(UserStatus.ACTIVE, user.status, "거부된 제재는 상태를 건드리지 않는다")
        assertNull(user.sanctionUntil)
    }

    @Test
    fun `ban은 BANNED로 바꾸고 프로필과 닉네임을 남긴다 — 탈퇴와 달리 점유를 풀지 않는다`() {
        val user = activeUser()

        user.ban()

        assertEquals(UserStatus.BANNED, user.status)
        assertEquals("코코집사", user.nickname, "영구 정지는 닉네임 점유를 풀지 않는다")
        assertNull(user.sanctionUntil)
    }

    @Test
    fun `forceWithdraw는 탈퇴와 같은 상태로 가고 프로필 점유를 해제한다`() {
        val user = activeUser()

        user.forceWithdraw()

        assertEquals(UserStatus.WITHDRAWN, user.status)
        assertNotNull(user.withdrawnAt)
        assertNull(user.nickname, "탈퇴 처리는 본인 탈퇴와 같이 닉네임 점유를 푼다")
    }

    @Test
    fun `영구 정지된 계정은 어떤 조치도 더 받지 않는다`() {
        for (apply in listOf<(User) -> Unit>({ it.restrict() }, { it.suspendUntil(future()) }, { it.ban() }, { it.forceWithdraw() })) {
            val user = activeUser()
            user.ban()

            val exception = assertFailsWith<BusinessException> { apply(user) }

            assertEquals(UserErrorCode.PERMANENT_BAN_IS_FINAL, exception.errorCode)
            assertEquals(UserStatus.BANNED, user.status, "거부된 조치는 상태를 바꾸지 않는다")
        }
    }

    @Test
    fun `탈퇴한 계정은 제재 대상이 아니다`() {
        for (apply in listOf<(User) -> Unit>({ it.restrict() }, { it.suspendUntil(future()) }, { it.ban() }, { it.forceWithdraw() })) {
            val user = activeUser()
            user.withdraw()

            val exception = assertFailsWith<BusinessException> { apply(user) }

            assertEquals(UserErrorCode.ALREADY_WITHDRAWN, exception.errorCode)
            assertEquals(UserStatus.WITHDRAWN, user.status)
        }
    }

    @Test
    fun `releaseSanction은 제재를 ACTIVE로 되돌리고 만료 시각을 비운다`() {
        val user = activeUser()
        user.suspendUntil(future())

        user.releaseSanction()

        assertEquals(UserStatus.ACTIVE, user.status)
        assertNull(user.sanctionUntil)
    }

    @Test
    fun `제재가 없으면 해제가 30019로 거부된다`() {
        val user = activeUser()

        val exception = assertFailsWith<BusinessException> { user.releaseSanction() }

        assertEquals(UserErrorCode.NO_ACTIVE_SANCTION, exception.errorCode)
    }

    @Test
    fun `영구 정지와 탈퇴는 해제할 수 없다`() {
        val banned = activeUser().apply { ban() }
        val withdrawn = activeUser().apply { withdraw() }

        assertEquals(UserErrorCode.NO_ACTIVE_SANCTION, assertFailsWith<BusinessException> { banned.releaseSanction() }.errorCode)
        assertEquals(UserStatus.BANNED, banned.status)
        assertEquals(UserErrorCode.NO_ACTIVE_SANCTION, assertFailsWith<BusinessException> { withdrawn.releaseSanction() }.errorCode)
        assertEquals(UserStatus.WITHDRAWN, withdrawn.status)
    }

    @Test
    fun `기간이 지난 임시 정지의 유효 상태는 ACTIVE지만 저장값은 SUSPENDED로 남는다`() {
        val user = activeUser()
        val until = LocalDateTime.now().plusDays(1)
        user.suspendUntil(until)

        assertEquals(UserStatus.SUSPENDED, user.effectiveStatus(until.minusSeconds(1)))
        assertEquals(UserStatus.ACTIVE, user.effectiveStatus(until))
        assertEquals(UserStatus.ACTIVE, user.effectiveStatus(until.plusSeconds(1)))
        assertEquals(UserStatus.SUSPENDED, user.status, "만료 판정은 저장값을 바꾸지 않는다")
        assertEquals(until, user.sanctionUntil)
    }

    @Test
    fun `만료 판정은 임시 정지에만 적용된다 — 이용 제한과 영구 정지는 시간이 지나도 그대로다`() {
        val now = LocalDateTime.now()
        val past = now.minusDays(1)

        assertEquals(UserStatus.RESTRICTED, UserStatus.effective(UserStatus.RESTRICTED, past, now))
        assertEquals(UserStatus.BANNED, UserStatus.effective(UserStatus.BANNED, past, now))
        assertEquals(UserStatus.WITHDRAWN, UserStatus.effective(UserStatus.WITHDRAWN, past, now))
        assertEquals(UserStatus.SUSPENDED, UserStatus.effective(UserStatus.SUSPENDED, null, now), "만료 시각이 없으면 풀리지 않는다")
    }

    @Test
    fun `계정 상태 판정 코드는 User의 100번대를, 이력 입력 검증 코드는 UserSanction의 100번대를 쓴다`() {
        assertEquals(30018 to 409, UserErrorCode.PERMANENT_BAN_IS_FINAL.code to UserErrorCode.PERMANENT_BAN_IS_FINAL.status)
        assertEquals(30019 to 409, UserErrorCode.NO_ACTIVE_SANCTION.code to UserErrorCode.NO_ACTIVE_SANCTION.status)
        assertEquals(30020 to 403, UserErrorCode.SELF_SANCTION_NOT_ALLOWED.code to UserErrorCode.SELF_SANCTION_NOT_ALLOWED.status)
        assertEquals(
            30021 to 409,
            UserErrorCode.ADMIN_CANNOT_BE_SANCTIONED.code to UserErrorCode.ADMIN_CANNOT_BE_SANCTIONED.status,
        )
        assertEquals(
            30022 to 400,
            UserErrorCode.SANCTION_PERIOD_NOT_IN_FUTURE.code to UserErrorCode.SANCTION_PERIOD_NOT_IN_FUTURE.status,
        )
        assertEquals(
            30800 to 400,
            UserErrorCode.SANCTION_REASON_DETAIL_REQUIRED.code to UserErrorCode.SANCTION_REASON_DETAIL_REQUIRED.status,
        )
        assertEquals(30801 to 400, UserErrorCode.SANCTION_PERIOD_REQUIRED.code to UserErrorCode.SANCTION_PERIOD_REQUIRED.status)
        assertEquals(
            30802 to 400,
            UserErrorCode.SANCTION_PERIOD_NOT_ALLOWED.code to UserErrorCode.SANCTION_PERIOD_NOT_ALLOWED.status,
        )
        assertEquals(30803 to 400, UserErrorCode.SANCTION_TEXT_TOO_LONG.code to UserErrorCode.SANCTION_TEXT_TOO_LONG.status)
    }
}
