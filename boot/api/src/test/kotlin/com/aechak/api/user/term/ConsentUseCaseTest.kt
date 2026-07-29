package com.aechak.api.user.term

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.user.term.usecase.ConsentUseCase
import com.aechak.application.user.term.usecase.command.SubmitConsentsCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.term.Term
import com.aechak.domain.user.term.enums.TermType
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import jakarta.persistence.Tuple
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class ConsentUseCaseTest : IntegrationTestBase() {
    @Autowired
    lateinit var consentUseCase: ConsentUseCase

    private fun seedTerm(
        type: TermType,
        isRequired: Boolean,
        isActive: Boolean = true,
    ): Long {
        lateinit var term: Term
        tx.executeWithoutResult {
            term = Term.of(type, isRequired, "1.0", "$type 약관", "$type 본문", LocalDateTime.of(2026, 7, 1, 0, 0), isActive)
            em.persist(term)
        }
        return term.id
    }

    private fun newPendingUser(): Long {
        lateinit var user: User
        tx.executeWithoutResult {
            user = User.preRegister()
            em.persist(user)
        }
        return user.id
    }

    private fun activate(userId: Long) {
        tx.executeWithoutResult {
            em
                .createQuery("update User u set u.status = :status where u.id = :id")
                .setParameter("status", UserStatus.ACTIVE)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    /** 저장된 동의 행을 (termId, isAgreed) 튜플로 — append 순서(id) 그대로. */
    private fun consentRows(userId: Long): List<Pair<Long, Boolean>> =
        em
            .createQuery(
                "select c.term.id as termId, c.isAgreed as agreed from ConsentRecord c where c.user.id = :userId order by c.id asc",
                Tuple::class.java,
            ).setParameter("userId", userId)
            .resultList
            .map { it.get("termId") as Long to it.get("agreed") as Boolean }

    private fun submit(
        userId: Long,
        vararg items: Pair<Long, Boolean>,
    ) = consentUseCase.submitConsents(
        SubmitConsentsCommand(userId, items.map { SubmitConsentsCommand.ConsentItem(it.first, it.second) }),
    )

    @Test
    fun `활성 약관만 필수 여부·본문과 함께 id 순으로 반환한다`() {
        seedTerm(TermType.SERVICE, isRequired = true)
        seedTerm(TermType.PRIVACY, isRequired = true)
        seedTerm(TermType.MARKETING, isRequired = false)
        seedTerm(TermType.LOCATION, isRequired = false, isActive = false)

        val terms = consentUseCase.getActiveTerms()

        assertEquals(listOf(TermType.SERVICE, TermType.PRIVACY, TermType.MARKETING), terms.map { it.type }, "비활성 LOCATION 제외")
        assertEquals(listOf(true, true, false), terms.map { it.isRequired })
        assertEquals("SERVICE 본문", terms.first().body, "본문 동봉 — 상세 EP 없음")
    }

    @Test
    fun `필수 약관 전체 동의 제출 시 항목별 동의 이력이 저장된다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val privacyId = seedTerm(TermType.PRIVACY, isRequired = true)
        val marketingId = seedTerm(TermType.MARKETING, isRequired = false)
        val userId = newPendingUser()

        submit(userId, serviceId to true, privacyId to true, marketingId to false)

        assertEquals(listOf(serviceId to true, privacyId to true, marketingId to false), consentRows(userId), "선택 false도 수집")
    }

    @Test
    fun `선택 약관을 생략해도 필수만 전체 동의면 저장된다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        seedTerm(TermType.MARKETING, isRequired = false)
        val userId = newPendingUser()

        submit(userId, serviceId to true)

        assertEquals(listOf(serviceId to true), consentRows(userId))
    }

    @Test
    fun `필수 약관을 누락하면 REQUIRED_CONSENT_MISSING으로 거부한다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        seedTerm(TermType.PRIVACY, isRequired = true)
        val userId = newPendingUser()

        val e = assertThrows<BusinessException> { submit(userId, serviceId to true) }

        assertEquals(UserErrorCode.REQUIRED_CONSENT_MISSING, e.errorCode)
        assertEquals(emptyList<Pair<Long, Boolean>>(), consentRows(userId), "거부 시 아무 행도 남지 않는다")
    }

    @Test
    fun `필수 약관을 false로 제출해도 REQUIRED_CONSENT_MISSING으로 거부한다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val privacyId = seedTerm(TermType.PRIVACY, isRequired = true)
        val userId = newPendingUser()

        val e = assertThrows<BusinessException> { submit(userId, serviceId to true, privacyId to false) }

        assertEquals(UserErrorCode.REQUIRED_CONSENT_MISSING, e.errorCode)
    }

    @Test
    fun `비활성 약관이 포함되면 INVALID_TERM으로 거부한다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val inactiveId = seedTerm(TermType.LOCATION, isRequired = false, isActive = false)
        val userId = newPendingUser()

        val e = assertThrows<BusinessException> { submit(userId, serviceId to true, inactiveId to true) }

        assertEquals(UserErrorCode.INVALID_TERM, e.errorCode)
    }

    @Test
    fun `존재하지 않는 약관 id면 INVALID_TERM으로 거부한다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val userId = newPendingUser()

        val e = assertThrows<BusinessException> { submit(userId, serviceId to true, 999_999L to true) }

        assertEquals(UserErrorCode.INVALID_TERM, e.errorCode)
    }

    @Test
    fun `ACTIVE 계정의 제출은 ONBOARDING_ALREADY_COMPLETED로 거부한다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val userId = newPendingUser()
        activate(userId)

        val e = assertThrows<BusinessException> { submit(userId, serviceId to true) }

        assertEquals(UserErrorCode.ONBOARDING_ALREADY_COMPLETED, e.errorCode)
    }

    @Test
    fun `재제출은 기존 행을 덮지 않고 새 행으로 append된다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val marketingId = seedTerm(TermType.MARKETING, isRequired = false)
        val userId = newPendingUser()

        submit(userId, serviceId to true, marketingId to false)
        submit(userId, serviceId to true, marketingId to true)

        val rows = consentRows(userId)
        assertEquals(4, rows.size, "append-only 원장 — 재제출도 새 행")
        assertEquals(marketingId to false, rows[1], "기존 행 불변")
        assertEquals(marketingId to true, rows[3], "유효 동의 = 최신 행")
    }
}
