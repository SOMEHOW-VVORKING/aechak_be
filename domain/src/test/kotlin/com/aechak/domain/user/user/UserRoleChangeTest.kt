package com.aechak.domain.user.user

import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.user.enums.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 계약 — role은 승격·강등 메서드로만 움직인다. 깨지면 관리자 자격이 의도 밖에서 생기거나 사라진다.
 */
class UserRoleChangeTest {
    private fun activeUser(): User = User.preRegister().apply { completeOnboarding("코코집사") }

    @Test
    fun `promoteToAdmin은 ACTIVE 일반 회원을 관리자로 바꾼다`() {
        val user = activeUser()

        user.promoteToAdmin()

        assertEquals(UserRole.ADMIN, user.role)
    }

    @Test
    fun `이미 관리자면 promoteToAdmin이 30012로 거부된다`() {
        val user = activeUser()
        user.promoteToAdmin()

        val exception = assertFailsWith<BusinessException> { user.promoteToAdmin() }

        assertEquals(UserErrorCode.USER_ALREADY_ADMIN, exception.errorCode)
    }

    @Test
    fun `관리자 역할 에러코드의 번호와 HTTP 상태는 계약이다`() {
        assertEquals(30012, UserErrorCode.USER_ALREADY_ADMIN.code)
        assertEquals(409, UserErrorCode.USER_ALREADY_ADMIN.status)
        assertEquals(30017, UserErrorCode.USER_NOT_PROMOTABLE.code)
        assertEquals(409, UserErrorCode.USER_NOT_PROMOTABLE.status)
    }

    @Test
    fun `ACTIVE가 아니면 promoteToAdmin이 거부된다`() {
        val pending = User.preRegister()

        assertFailsWith<IllegalStateException> { pending.promoteToAdmin() }
        assertEquals(UserRole.GENERAL, pending.role)
    }

    @Test
    fun `demoteToGeneral은 관리자를 일반 회원으로 바꾼다`() {
        val user = activeUser()
        user.promoteToAdmin()

        user.demoteToGeneral()

        assertEquals(UserRole.GENERAL, user.role)
    }

    @Test
    fun `이미 일반 회원이면 demoteToGeneral은 아무것도 하지 않는다`() {
        val user = activeUser()

        user.demoteToGeneral()

        assertEquals(UserRole.GENERAL, user.role)
    }

    @Test
    fun `강등은 상태를 보지 않는다 — 비활성 계정도 자격을 회수할 수 있다`() {
        val user = activeUser()
        user.promoteToAdmin()
        user.withdraw()

        user.demoteToGeneral()

        assertEquals(UserRole.GENERAL, user.role)
    }
}
