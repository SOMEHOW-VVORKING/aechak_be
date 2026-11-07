package com.aechak.domain.user.user

import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 운영자 프로필 초기화 — 이미지와 소개글만 비우고 닉네임과 계정 상태는 그대로 둔다. */
class UserProfileResetTest {
    private fun activeUser(): User =
        User.preRegister().apply {
            completeOnboarding("코코집사")
            updateProfile("코코집사", "괴롭힘 문구", "users/1/profile.png")
        }

    @Test
    fun `프로필 이미지와 소개글을 비우고 닉네임은 남긴다`() {
        val user = activeUser()

        user.resetProfileByOperator()

        assertNull(user.profileImageKey)
        assertNull(user.bio)
        assertEquals("코코집사", user.nickname)
    }

    @Test
    fun `제재 중인 계정도 초기화한다`() {
        val user = activeUser().apply { ban() }

        user.resetProfileByOperator()

        assertNull(user.profileImageKey)
        assertNull(user.bio)
    }

    @Test
    fun `프로필이 없는 온보딩 전 계정과 탈퇴 계정은 30024로 거부된다`() {
        val pending = User.preRegister()
        val withdrawn = activeUser().apply { withdraw() }

        assertEquals(
            UserErrorCode.PROFILE_NOT_RESETTABLE,
            assertFailsWith<BusinessException> { pending.resetProfileByOperator() }.errorCode,
        )
        assertEquals(
            UserErrorCode.PROFILE_NOT_RESETTABLE,
            assertFailsWith<BusinessException> { withdrawn.resetProfileByOperator() }.errorCode,
        )
        assertEquals(30024, UserErrorCode.PROFILE_NOT_RESETTABLE.code)
        assertEquals(409, UserErrorCode.PROFILE_NOT_RESETTABLE.status)
    }
}
