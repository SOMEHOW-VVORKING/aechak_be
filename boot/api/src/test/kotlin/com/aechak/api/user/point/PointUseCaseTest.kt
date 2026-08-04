package com.aechak.api.user.point

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PointUseCaseTest : IntegrationTestBase() {
    @Autowired
    lateinit var pointUseCase: PointUseCase

    private fun newUser(): Long {
        lateinit var user: User
        tx.executeWithoutResult {
            user = User.preRegister()
            em.persist(user)
        }
        return user.id
    }

    /** point_balance 직접 시드 — 갱신 경로(주문/리뷰 도메인)가 미구현이라 테스트가 비정규화 컬럼을 채운다. */
    private fun seedBalance(
        userId: Long,
        balance: Long,
    ) = tx.executeWithoutResult {
        em
            .createQuery("update User u set u.pointBalance = :balance where u.id = :id")
            .setParameter("balance", balance)
            .setParameter("id", userId)
            .executeUpdate()
    }

    @Test
    fun `신규 유저의 잔액은 0이다`() {
        val userId = newUser()

        assertEquals(0L, pointUseCase.getMyPointBalance(userId).balance)
    }

    @Test
    fun `users의 point_balance 값을 그대로 반환한다`() {
        val userId = newUser()
        seedBalance(userId, 1200L)

        assertEquals(1200L, pointUseCase.getMyPointBalance(userId).balance)
    }

    @Test
    fun `타 유저의 잔액과 섞이지 않는다`() {
        val userId = newUser()
        val otherId = newUser()
        seedBalance(userId, 1000L)
        seedBalance(otherId, 700L)

        assertEquals(1000L, pointUseCase.getMyPointBalance(userId).balance)
        assertEquals(700L, pointUseCase.getMyPointBalance(otherId).balance)
    }
}
