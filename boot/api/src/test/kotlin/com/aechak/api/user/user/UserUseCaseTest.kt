package com.aechak.api.user.user

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.user.term.usecase.ConsentUseCase
import com.aechak.application.user.term.usecase.command.SubmitConsentsCommand
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.command.SetNicknameCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.social.SocialIdentity
import com.aechak.domain.user.social.enums.SocialProvider
import com.aechak.domain.user.term.ConsentRecord
import com.aechak.domain.user.term.Term
import com.aechak.domain.user.term.enums.TermType
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.text.Normalizer
import java.time.LocalDateTime

class UserUseCaseTest : IntegrationTestBase() {
    @Autowired
    lateinit var userUseCase: UserUseCase

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

    /** 정상 플로우 동의 — 넘긴 약관 전부 isAgreed=true로 제출. */
    private fun consentAll(
        userId: Long,
        vararg termIds: Long,
    ) = consentUseCase.submitConsents(
        SubmitConsentsCommand(userId, termIds.map { SubmitConsentsCommand.ConsentItem(it, true) }),
    )

    /** 게이트 경계 상태 구성용 — 검증 없이 원장에 행을 직접 append한다. */
    private fun appendConsentRow(
        userId: Long,
        termId: Long,
        isAgreed: Boolean,
    ) = tx.executeWithoutResult {
        val user = em.find(User::class.java, userId)
        val term = em.find(Term::class.java, termId)
        em.persist(ConsentRecord.record(user, term, isAgreed))
    }

    private fun linkSocial(
        userId: Long,
        email: String?,
    ) = tx.executeWithoutResult {
        val user = em.find(User::class.java, userId)
        em.persist(SocialIdentity.link(user, SocialProvider.KAKAO, "pid-$userId", email))
    }

    private fun setNickname(
        userId: Long,
        nickname: String,
    ) = userUseCase.setNickname(SetNicknameCommand(userId, nickname))

    private fun statusInDb(userId: Long): UserStatus =
        em
            .createQuery("select u.status from User u where u.id = :id", UserStatus::class.java)
            .setParameter("id", userId)
            .singleResult

    @Test
    fun `동의 없이 닉네임을 설정하면 REQUIRED_CONSENT_MISSING으로 거부한다`() {
        seedTerm(TermType.SERVICE, isRequired = true)
        seedTerm(TermType.PRIVACY, isRequired = true)
        val userId = newPendingUser()

        val e = assertThrows<BusinessException> { setNickname(userId, "코코집사") }

        assertEquals(UserErrorCode.REQUIRED_CONSENT_MISSING, e.errorCode)
        assertEquals(UserStatus.PENDING_ONBOARDING, statusInDb(userId), "화면 순서 우회로는 ACTIVE 불가")
    }

    @Test
    fun `필수 약관의 최신 행이 false면 과거 동의가 있어도 게이트가 막는다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val privacyId = seedTerm(TermType.PRIVACY, isRequired = true)
        val userId = newPendingUser()
        appendConsentRow(userId, serviceId, isAgreed = true)
        appendConsentRow(userId, serviceId, isAgreed = false) // 최신 행이 철회 — 유효 동의 아님
        appendConsentRow(userId, privacyId, isAgreed = true)

        val e = assertThrows<BusinessException> { setNickname(userId, "코코집사") }

        assertEquals(UserErrorCode.REQUIRED_CONSENT_MISSING, e.errorCode, "유효 동의 = 최신 행 기준")
    }

    @Test
    fun `필수 동의 후 닉네임을 설정하면 저장과 ACTIVE 전이가 함께 반영된다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val privacyId = seedTerm(TermType.PRIVACY, isRequired = true)
        val userId = newPendingUser()
        consentAll(userId, serviceId, privacyId)

        val me = setNickname(userId, "코코집사")

        assertEquals(UserStatus.ACTIVE, me.status, "전이 결과가 응답에 즉시 실린다 — FE는 이걸로 홈 라우팅")
        assertEquals("코코집사", me.nickname)
        assertEquals(UserStatus.ACTIVE, statusInDb(userId), "커밋 즉시 DB 반영 — 재로그인 불필요(상태검증은 DB 기준)")
    }

    @Test
    fun `같은 값 재설정은 멱등으로 성공한다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val userId = newPendingUser()
        consentAll(userId, serviceId)
        setNickname(userId, "코코집사")

        val me = setNickname(userId, "코코집사")

        assertEquals("코코집사", me.nickname)
        assertEquals(UserStatus.ACTIVE, me.status)
    }

    @Test
    fun `ACTIVE 계정은 닉네임을 재변경할 수 있다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val userId = newPendingUser()
        consentAll(userId, serviceId)
        setNickname(userId, "코코집사")

        val me = setNickname(userId, "새로운집사")

        assertEquals("새로운집사", me.nickname)
    }

    @Test
    fun `타 유저가 선점한 닉네임은 DUPLICATE_NICKNAME으로 거부한다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val ownerId = newPendingUser()
        consentAll(ownerId, serviceId)
        setNickname(ownerId, "코코집사")
        val userId = newPendingUser()
        consentAll(userId, serviceId)

        val e = assertThrows<BusinessException> { setNickname(userId, "코코집사") }

        assertEquals(UserErrorCode.DUPLICATE_NICKNAME, e.errorCode, "DB UNIQUE가 최종 판정 — 커밋 시점 위반을 30002로 번역")
        assertEquals(UserStatus.PENDING_ONBOARDING, statusInDb(userId), "실패 시 전이도 롤백")
    }

    @Test
    fun `대소문자만 다른 닉네임도 중복으로 판정한다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val ownerId = newPendingUser()
        consentAll(ownerId, serviceId)
        setNickname(ownerId, "Coco99")
        val userId = newPendingUser()
        consentAll(userId, serviceId)

        val e = assertThrows<BusinessException> { setNickname(userId, "coco99") }

        assertEquals(UserErrorCode.DUPLICATE_NICKNAME, e.errorCode, "ci collation — 스왑 사칭 차단")
    }

    @Test
    fun `조합형(NFD) 입력은 완성형(NFC)으로 정규화되어 저장된다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val userId = newPendingUser()
        consentAll(userId, serviceId)
        val nfd = Normalizer.normalize("가나다", Normalizer.Form.NFD)

        val me = setNickname(userId, nfd)

        assertEquals("가나다", me.nickname, "저장은 항상 NFC — 육안 동일·바이트 상이 중복 차단")
        assertEquals(3, me.nickname!!.length)
    }

    @Test
    fun `형식 위반 닉네임은 INVALID_NICKNAME으로 거부한다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val userId = newPendingUser()
        consentAll(userId, serviceId)

        listOf("a", "열세글자를넘겨버린닉네임이다", "공백 포함", "이모지🐶", "자음ㄱㄴ").forEach { invalid ->
            val e = assertThrows<BusinessException>("입력: $invalid") { setNickname(userId, invalid) }
            assertEquals(UserErrorCode.INVALID_NICKNAME, e.errorCode, "입력: $invalid")
        }
    }

    @Test
    fun `닉네임 검사 - 미사용은 true, 타인 선점은 false, 본인 현재 닉네임은 true다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val userId = newPendingUser()
        consentAll(userId, serviceId)
        setNickname(userId, "코코집사")
        val otherId = newPendingUser()

        assertTrue(userUseCase.checkNickname(userId, "미사용닉네임"))
        assertFalse(userUseCase.checkNickname(otherId, "코코집사"), "타인 선점")
        assertTrue(userUseCase.checkNickname(userId, "코코집사"), "본인 현재 닉네임은 사용 가능 — 프로필 수정 화면 모순 제거")
    }

    @Test
    fun `닉네임 검사도 형식 위반이면 INVALID_NICKNAME으로 거부한다`() {
        val userId = newPendingUser()

        val e = assertThrows<BusinessException> { userUseCase.checkNickname(userId, "공백 포함") }

        assertEquals(UserErrorCode.INVALID_NICKNAME, e.errorCode)
    }

    @Test
    fun `내 정보 - PENDING은 status만 있고 프로필 계열은 null이다`() {
        val userId = newPendingUser()

        val me = userUseCase.getMe(userId)

        assertEquals(UserStatus.PENDING_ONBOARDING, me.status)
        assertNull(me.nickname)
        assertNull(me.profileImageUrl)
        assertNull(me.bio)
        assertNull(me.phoneNumber, "휴대폰 인증 전까지 항상 null")
        assertFalse(me.isPhoneVerified)
    }

    @Test
    fun `내 정보 - ACTIVE는 닉네임과 소셜 이메일이 채워진다`() {
        val serviceId = seedTerm(TermType.SERVICE, isRequired = true)
        val userId = newPendingUser()
        linkSocial(userId, "coco@kakao.com")
        consentAll(userId, serviceId)
        setNickname(userId, "코코집사")

        val me = userUseCase.getMe(userId)

        assertEquals(UserStatus.ACTIVE, me.status)
        assertEquals("코코집사", me.nickname)
        assertEquals("coco@kakao.com", me.email)
        assertNull(me.profileImageUrl, "프로필 이미지 미등록 — key 없으면 URL도 null")
    }

    @Test
    fun `내 정보 - 소셜 이메일 미제공(애플 가림)이면 email은 null이다`() {
        val userId = newPendingUser()
        linkSocial(userId, email = null)

        assertNull(userUseCase.getMe(userId).email)
    }
}
