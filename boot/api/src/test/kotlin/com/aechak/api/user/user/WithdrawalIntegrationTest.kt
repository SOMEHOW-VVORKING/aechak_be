package com.aechak.api.user.user

import com.aechak.api.auth.cookie.RefreshCookieFactory
import com.aechak.api.support.FakeFileStorage
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.service.RejoinPolicy
import com.aechak.application.file.port.FileStorage
import com.aechak.application.user.withdrawal.usecase.WithdrawalUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.seller.application.SellerApplication
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.application.enums.BusinessType
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.awaitility.Awaitility.await
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합 테스트. 회원 탈퇴 요청을 보안 필터, MySQL, Redis까지 포함해 검증한다.
 * 깨지면 막아야 할 탈퇴가 통과하거나 승인 경합 뒤 셀러가 남은 채 탈퇴한 계정이 생긴다.
 * 세션과 프로필 이미지는 DB 커밋 후 정리되므로 tx.execute로 실제 트랜잭션을 커밋한다.
 * 모든 refresh 세션이 삭제되는지는 Redis 키를 직접 조회해 확인한다.
 */
class WithdrawalIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var redis: StringRedisTemplate

    @Autowired
    private lateinit var fileStorage: FileStorage

    @Autowired
    private lateinit var rejoinPolicy: RejoinPolicy

    @Autowired
    private lateinit var withdrawalUseCase: WithdrawalUseCase

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `탈퇴하면 204와 함께 refresh 쿠키 만료 헤더가 내려간다`() {
        val userId = createOnboardedUser("코코집사")

        mockMvc
            .perform(withdrawRequest(userId))
            .andExpect(status().isNoContent)
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsCookieExpiry()))
    }

    @Test
    fun `탈퇴하면 상태가 WITHDRAWN으로 바뀌고 프로필 행이 사라진다`() {
        val userId = createOnboardedUser("코코집사")

        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        assertEquals(UserStatus.WITHDRAWN, statusOf(userId))
        assertEquals(0, countProfiles(userId))
    }

    @Test
    fun `탈퇴하면 Redis의 전 세션이 실제로 지워진다`() {
        val providerId = "kakao-session"
        val userId = signUpAndOnboard(providerId, "세션집사")
        mockMvc.perform(loginRequest(providerId)).andExpect(status().isOk) // 두 번째 기기 세션
        assertTrue(sessionKeys(userId).size >= 2, "탈퇴 전에 세션이 남아 있어야 검증이 의미 있다")

        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        assertTrue(sessionKeys(userId).isEmpty(), "탈퇴 후 refresh 세션 키가 남아 있다")
    }

    @Test
    fun `탈퇴하면 소셜 이메일이 삭제된다`() {
        val providerId = "kakao-pii"
        val userId = signUpAndOnboard(providerId, "삭제집사")
        assertEquals("owner@example.com", socialEmailOf(providerId))

        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        assertNull(socialEmailOf(providerId), "탈퇴 후 소셜 이메일이 남아 있다")
    }

    @Test
    fun `탈퇴하면 프로필 이미지가 스토리지에서도 삭제된다`() {
        val imageKey = "users/profile/withdrawal-target.webp"
        val userId = createOnboardedUser("사진집사", imageKey)

        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        // 프로필 행뿐 아니라 공개 버킷의 이미지도 삭제되어야 한다.
        assertTrue(imageKey in (fileStorage as FakeFileStorage).deletedKeys, "프로필 이미지가 스토리지에 남아 있다")
    }

    @Test
    fun `탈퇴한 계정이 쓰던 닉네임을 다른 유저가 다시 쓸 수 있다`() {
        val userId = createOnboardedUser("코코집사")
        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        // 탈퇴한 사용자의 닉네임을 삭제해야 다른 사용자가 같은 닉네임을 저장할 수 있다.
        val other = createOnboardedUser("코코집사")

        assertNotEquals(userId, other)
    }

    @Test
    fun `탈퇴 후에는 같은 액세스 토큰으로 API를 쓸 수 없다`() {
        val userId = createOnboardedUser("코코집사")
        val token = mintAccessToken(userId)
        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        mockMvc
            .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ACCOUNT_BLOCKED.code))
    }

    @Test
    fun `탈퇴 요청을 두 번 보내면 두 번째는 상태 필터가 막는다`() {
        val userId = createOnboardedUser("코코집사")
        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        // 두 번째 요청은 컨트롤러에 도달하기 전에 UserStatusFilter에서 차단된다.
        mockMvc
            .perform(withdrawRequest(userId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ACCOUNT_BLOCKED.code))
    }

    @Test
    fun `온보딩 미완료 계정도 탈퇴할 수 있고 탈퇴 가능 여부 조회까지 열려 있다`() {
        val providerId = "kakao-pending"
        mockMvc.perform(loginRequest(providerId)).andExpect(status().isOk)
        val userId = userIdLinkedTo(providerId)

        // 온보딩을 완료하지 않은 사용자도 앱에서 계정을 삭제할 수 있어야 한다.
        mockMvc.perform(withdrawalCheckRequest(userId)).andExpect(status().isOk)
        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        assertEquals(UserStatus.WITHDRAWN, statusOf(userId))
    }

    @Test
    fun `차단 사유가 없으면 withdrawable true와 빈 blockers를 내려준다`() {
        val userId = createOnboardedUser("코코집사")

        mockMvc
            .perform(withdrawalCheckRequest(userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.withdrawable").value(true))
            .andExpect(jsonPath("$.data.blockers").isEmpty())
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus::class, names = ["ACTIVE", "PAUSED", "SUSPENDED", "WITHDRAWAL_REQUESTED"])
    fun `퇴점하지 않은 셀러는 상태와 상관없이 SELLER_ACCOUNT 1건으로 막힌다`(sellerStatus: SellerStatus) {
        val userId = createOnboardedUser("셀러집사")
        seedSeller(userId, sellerStatus)

        mockMvc
            .perform(withdrawalCheckRequest(userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.withdrawable").value(false))
            .andExpect(jsonPath("$.data.blockers.length()").value(1))
            .andExpect(jsonPath("$.data.blockers[0].reason").value("SELLER_ACCOUNT"))
            .andExpect(jsonPath("$.data.blockers[0].count").value(1))
    }

    @Test
    fun `퇴점한 셀러는 탈퇴할 수 있다`() {
        val userId = createOnboardedUser("퇴점집사")
        seedSeller(userId, SellerStatus.WITHDRAWN)

        mockMvc
            .perform(withdrawalCheckRequest(userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.withdrawable").value(true))
            .andExpect(jsonPath("$.data.blockers").isEmpty())
        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)
    }

    @Test
    fun `만료 전 결제대기 주문그룹만 PAYMENT_IN_PROGRESS로 센다`() {
        val userId = createOnboardedUser("결제집사")
        val now = LocalDateTime.now()
        seedOrderGroup(userId, now.plusMinutes(10))
        seedOrderGroup(userId, now.plusMinutes(10))
        seedOrderGroup(userId, now.minusMinutes(1)) // 만료된 결제대기
        seedOrderGroup(userId, now.plusMinutes(10), OrderGroupStatus.PAID)
        seedOrderGroup(userId, now.plusMinutes(10), OrderGroupStatus.CANCELLED)
        seedOrderGroup(userId + 1000, now.plusMinutes(10)) // 다른 구매자

        mockMvc
            .perform(withdrawalCheckRequest(userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.withdrawable").value(false))
            .andExpect(jsonPath("$.data.blockers.length()").value(1))
            .andExpect(jsonPath("$.data.blockers[0].reason").value("PAYMENT_IN_PROGRESS"))
            .andExpect(jsonPath("$.data.blockers[0].count").value(2))
    }

    @Test
    fun `만료된 결제대기 주문그룹만 있으면 탈퇴할 수 있다`() {
        val userId = createOnboardedUser("만료집사")
        seedOrderGroup(userId, LocalDateTime.now().minusMinutes(1))

        mockMvc
            .perform(withdrawalCheckRequest(userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.withdrawable").value(true))
        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)
    }

    @Test
    fun `셀러 계정과 결제 진행 중 주문이 함께 있으면 enum 선언 순서로 내려준다`() {
        val userId = createOnboardedUser("겹친집사")
        seedOrderGroup(userId, LocalDateTime.now().plusMinutes(10))
        seedSeller(userId)

        mockMvc
            .perform(withdrawalCheckRequest(userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.withdrawable").value(false))
            .andExpect(jsonPath("$.data.blockers.length()").value(2))
            .andExpect(jsonPath("$.data.blockers[0].reason").value("SELLER_ACCOUNT"))
            .andExpect(jsonPath("$.data.blockers[0].count").value(1))
            .andExpect(jsonPath("$.data.blockers[1].reason").value("PAYMENT_IN_PROGRESS"))
            .andExpect(jsonPath("$.data.blockers[1].count").value(1))
    }

    @Test
    fun `입점 신청 중인 유저는 탈퇴 가능 여부 조회에서 막히지 않는다`() {
        val userId = createOnboardedUser("신청집사")
        seedApplication(userId, ApplicationStatus.SUBMITTED)

        mockMvc
            .perform(withdrawalCheckRequest(userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.withdrawable").value(true))
            .andExpect(jsonPath("$.data.blockers").isEmpty())
    }

    @Test
    fun `차단 사유가 있으면 DELETE는 409 30011이고 계정은 그대로다`() {
        val userId = createOnboardedUser("막힌집사")
        seedSeller(userId)

        mockMvc
            .perform(withdrawRequest(userId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.WITHDRAWAL_BLOCKED.code))
            .andExpect(jsonPath("$.message").value(UserErrorCode.WITHDRAWAL_BLOCKED.message))

        assertEquals(UserStatus.ACTIVE, statusOf(userId), "막힌 탈퇴가 계정 상태를 바꿨다")
    }

    @Test
    fun `결제 진행 중 주문으로 막히면 입점 신청 취소도 롤백된다`() {
        val userId = createOnboardedUser("롤백집사")
        seedOrderGroup(userId, LocalDateTime.now().plusMinutes(10))
        val applicationId = seedApplication(userId, ApplicationStatus.SUBMITTED)

        mockMvc
            .perform(withdrawRequest(userId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.WITHDRAWAL_BLOCKED.code))

        assertEquals(UserStatus.ACTIVE, statusOf(userId))
        assertEquals(ApplicationStatus.SUBMITTED, applicationStatusOf(applicationId), "막힌 탈퇴가 신청서를 취소한 채 커밋됐다")
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus::class, names = ["DRAFT", "SUBMITTED", "REVIEWING", "REJECTED"])
    fun `승인 전 입점 신청은 탈퇴와 함께 CANCELLED가 된다`(applicationStatus: ApplicationStatus) {
        val userId = createOnboardedUser("취소집사")
        val applicationId = seedApplication(userId, applicationStatus)

        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        assertEquals(ApplicationStatus.CANCELLED, applicationStatusOf(applicationId), "$applicationStatus 신청이 탈퇴 후에도 남아 있다")
        assertEquals(UserStatus.WITHDRAWN, statusOf(userId))
    }

    @Test
    fun `입점 승인이 탈퇴보다 먼저 커밋되면 탈퇴는 30011로 끝나고 셀러는 남는다`() {
        val userId = createOnboardedUser("경합집사")
        val applicationId = seedApplication(userId, ApplicationStatus.SUBMITTED)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            // 승인 트랜잭션을 흉내 낸다. 신청서 행을 UPDATE한 채로 커밋을 미룬다
            val approval =
                pool.submit(
                    Callable {
                        tx.execute {
                            em.find(SellerApplication::class.java, applicationId).approve(reviewerAdminId = 10L)
                            em.persist(Seller.open(userId, "경합상점", 3000L))
                            em.flush()
                            locked.countDown()
                            release.await(10, TimeUnit.SECONDS)
                        }
                    },
                )
            assertTrue(locked.await(10, TimeUnit.SECONDS), "승인 쪽이 신청서 행을 먼저 잡아야 한다")
            val withdrawal = pool.submit(Callable { runCatching { withdrawalUseCase.withdraw(userId) }.exceptionOrNull() })
            await().atMost(Duration.ofSeconds(10)).until { statementRunning("update seller_applications") }
            release.countDown()
            approval.get(10, TimeUnit.SECONDS)
            val error = withdrawal.get(10, TimeUnit.SECONDS)
            assertEquals(UserErrorCode.WITHDRAWAL_BLOCKED, (error as? BusinessException)?.errorCode, "승인이 이기면 탈퇴는 30011이어야 한다")
        } finally {
            release.countDown()
            pool.shutdownNow()
        }

        assertEquals(UserStatus.ACTIVE, statusOf(userId), "탈퇴는 롤백돼야 한다")
        assertEquals(ApplicationStatus.APPROVED, applicationStatusOf(applicationId))
        assertEquals(1L, singleLong("select count(s) from Seller s where s.userId = :param", userId))
    }

    @Test
    fun `퇴점한 셀러의 승인된 신청서는 탈퇴 후에도 APPROVED로 남는다`() {
        val userId = createOnboardedUser("승인집사")
        val applicationId = seedApplication(userId, ApplicationStatus.APPROVED)
        seedSeller(userId, SellerStatus.WITHDRAWN)

        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        assertEquals(ApplicationStatus.APPROVED, applicationStatusOf(applicationId), "탈퇴가 승인된 신청서를 취소했다")
        assertEquals(UserStatus.WITHDRAWN, statusOf(userId))
    }

    @Test
    fun `입점 신청이 있는 계정에 탈퇴가 동시에 두 번 들어오면 늦은 쪽은 30003으로 끝난다`() {
        val userId = createOnboardedUser("두번누른집사")
        val applicationId = seedApplication(userId, ApplicationStatus.SUBMITTED)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            // 먼저 들어온 탈퇴를 흉내 낸다. 신청서와 계정을 바꾸고 프로필 행을 지운 채로 커밋을 미룬다
            val first =
                pool.submit(
                    Callable {
                        tx.execute {
                            em.find(SellerApplication::class.java, applicationId).cancel()
                            em.find(User::class.java, userId).withdraw()
                            em.flush()
                            locked.countDown()
                            release.await(10, TimeUnit.SECONDS)
                        }
                    },
                )
            assertTrue(locked.await(10, TimeUnit.SECONDS), "먼저 들어온 탈퇴가 행 잠금을 먼저 잡아야 한다")
            val second = pool.submit(Callable { runCatching { withdrawalUseCase.withdraw(userId) }.exceptionOrNull() })
            // 늦은 탈퇴는 flush 때 orphan 프로필 DELETE를 먼저 실행하고 그 행 잠금을 기다린다
            await().atMost(Duration.ofSeconds(10)).until { statementRunning("delete from user_profiles") }
            release.countDown()
            first.get(10, TimeUnit.SECONDS)
            val error = second.get(10, TimeUnit.SECONDS)
            assertEquals(UserErrorCode.ALREADY_WITHDRAWN, (error as? BusinessException)?.errorCode, "늦은 탈퇴는 30003이어야 한다")
        } finally {
            release.countDown()
            pool.shutdownNow()
        }

        assertEquals(UserStatus.WITHDRAWN, statusOf(userId))
        assertEquals(ApplicationStatus.CANCELLED, applicationStatusOf(applicationId))
    }

    @Test
    fun `재가입 제한 기간 중에 같은 소셜 계정으로 다시 로그인하면 언제부터 가능한지와 함께 거부된다`() {
        val providerId = "kakao-in-grace"
        withdraw(signUpAndOnboard(providerId, "탈퇴할집사"))

        // 방금 탈퇴했으므로 지금 시각으로 계산한 재가입 시점이 안내에 실려야 한다.
        val allowedFrom = rejoinPolicy.allowedFrom(LocalDateTime.now()).format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))

        mockMvc
            .perform(loginRequest(providerId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.REJOIN_BLOCKED.code))
            .andExpect(jsonPath("$.message").value(containsString(allowedFrom)))
    }

    @Test
    fun `재가입 제한 기간이 지나면 새 계정으로 재가입되고 옛 계정은 탈퇴 상태로 남는다`() {
        val providerId = "kakao-after-grace"
        val oldUserId = signUpAndOnboard(providerId, "보리집사")
        withdraw(oldUserId)
        backdateWithdrawalPastRejoinBlock(oldUserId)

        mockMvc
            .perform(loginRequest(providerId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.user.isNew").value(true))
            .andExpect(jsonPath("$.data.user.status").value(UserStatus.PENDING_ONBOARDING.name))

        val newUserId = userIdLinkedTo(providerId)
        assertNotEquals(oldUserId, newUserId) // 옛 계정을 되살리지 않고 새 계정으로 시작한다
        assertEquals(UserStatus.WITHDRAWN, statusOf(oldUserId))
        assertEquals(UserStatus.PENDING_ONBOARDING, statusOf(newUserId))
    }

    @Test
    fun `재가입한 계정도 다시 탈퇴할 수 있다`() {
        val providerId = "kakao-twice"
        val oldUserId = signUpAndOnboard(providerId, "두번집사")
        withdraw(oldUserId)
        backdateWithdrawalPastRejoinBlock(oldUserId)
        mockMvc.perform(loginRequest(providerId)).andExpect(status().isOk)
        val newUserId = userIdLinkedTo(providerId)

        withdraw(newUserId)

        assertEquals(UserStatus.WITHDRAWN, statusOf(oldUserId))
        assertEquals(UserStatus.WITHDRAWN, statusOf(newUserId))
    }

    /** 소셜 연결 없이 ACTIVE 상태의 테스트 사용자를 생성한다. */
    private fun createOnboardedUser(
        nickname: String,
        profileImageKey: String? = null,
    ): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            user.completeOnboarding(nickname)
            profileImageKey?.let { user.updateProfile(nickname, null, it) }
            em.flush()
            user.id
        }!!

    /** 소셜 로그인과 온보딩을 마친 테스트 사용자를 생성한다. */
    private fun signUpAndOnboard(
        providerId: String,
        nickname: String,
    ): Long {
        mockMvc.perform(loginRequest(providerId)).andExpect(status().isOk)
        val userId = userIdLinkedTo(providerId)
        tx.execute { em.find(User::class.java, userId).completeOnboarding(nickname) }
        return userId
    }

    private fun seedSeller(
        userId: Long,
        status: SellerStatus = SellerStatus.ACTIVE,
    ) {
        tx.execute {
            em.persist(Seller.open(userId, "스토어-$userId", 3000L))
            em.flush()
            em
                .createQuery("update Seller s set s.status = :st where s.userId = :id")
                .setParameter("st", status)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    /** DRAFT, SUBMITTED, REVIEWING, APPROVED, REJECTED만 만든다. REVIEWING은 도메인 전이가 없어 직접 바꾼다 */
    private fun seedApplication(
        userId: Long,
        status: ApplicationStatus,
    ): Long =
        tx.execute {
            val application = SellerApplication.draft(userId, BusinessType.PERSONAL_GENERAL)
            if (status != ApplicationStatus.DRAFT) application.submit()
            if (status == ApplicationStatus.APPROVED) application.approve(10L)
            if (status == ApplicationStatus.REJECTED) application.reject(10L, "서류 미비")
            em.persist(application)
            em.flush()
            if (status == ApplicationStatus.REVIEWING) {
                em
                    .createQuery("update SellerApplication a set a.status = :st where a.id = :id")
                    .setParameter("st", status)
                    .setParameter("id", application.id)
                    .executeUpdate()
            }
            application.id
        }!!

    private fun seedOrderGroup(
        buyerId: Long,
        expiresAt: LocalDateTime,
        status: OrderGroupStatus = OrderGroupStatus.PENDING_PAYMENT,
    ) {
        tx.execute {
            val group =
                OrderGroup.create(
                    buyerId = buyerId,
                    deliveryAddressId = 1L,
                    deliveryAddress =
                        DeliveryAddressSnapshot(
                            receiverNameEnc = "enc",
                            contactNumberEnc = "enc",
                            zipCode = "06236",
                            baseAddress = "서울시 강남구 테헤란로 1",
                            detailAddress = null,
                            deliveryMemo = null,
                        ),
                    usedPoint = 0L,
                    totalProductAmount = 10_000L,
                    totalShippingFee = 3_000L,
                    idempotencyKey = UUID.randomUUID().toString(),
                    expiresAt = expiresAt,
                )
            when (status) {
                OrderGroupStatus.PAID -> group.markPaid()
                OrderGroupStatus.CANCELLED -> group.cancelUnpaid()
                else -> Unit
            }
            em.persist(group)
        }
    }

    private fun applicationStatusOf(id: Long): ApplicationStatus = tx.execute { em.find(SellerApplication::class.java, id).status }!!

    /** 다른 커넥션이 sqlPrefix로 시작하는 문장을 실행 중이면 true. 같은 DB 계정의 스레드는 PROCESS 권한 없이 보인다 */
    private fun statementRunning(sqlPrefix: String): Boolean =
        tx.execute {
            (
                em
                    .createNativeQuery("select count(*) from information_schema.processlist where info like concat(:prefix, '%')")
                    .setParameter("prefix", sqlPrefix)
                    .singleResult as Number
            ).toLong() > 0
        }!!

    private fun withdraw(userId: Long) = mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

    private fun withdrawRequest(userId: Long): RequestBuilder =
        delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}")

    private fun withdrawalCheckRequest(userId: Long): RequestBuilder =
        get("/api/v1/users/me/withdrawal/check")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}")

    /** 테스트용 검증기가 읽을 수 있는 "providerId:email" 형식으로 로그인한다. */
    private fun loginRequest(providerId: String): RequestBuilder =
        post("/api/v1/auth/login/kakao")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"idToken":"$providerId:owner@example.com"}""")

    /** 운영 어댑터와 같은 키 형식으로 Redis의 refresh 세션을 조회한다. */
    private fun sessionKeys(userId: Long): Set<String> = redis.keys("refresh:$userId:*")

    private fun statusOf(userId: Long): UserStatus = tx.execute { em.find(User::class.java, userId).status }!!

    private fun socialEmailOf(providerId: String): String? =
        tx.execute {
            em
                .createQuery("select i.email from SocialIdentity i where i.providerId = :param", String::class.java)
                .setParameter("param", providerId)
                .resultList
                .firstOrNull()
        }

    private fun userIdLinkedTo(providerId: String): Long =
        singleLong("select i.user.id from SocialIdentity i where i.providerId = :param", providerId)

    private fun countProfiles(userId: Long): Long = singleLong("select count(p) from UserProfile p where p.userId = :param", userId)

    private fun singleLong(
        jpql: String,
        param: Any,
    ): Long =
        tx.execute {
            em
                .createQuery(jpql, Long::class.javaObjectType)
                .setParameter("param", param)
                .singleResult
        }!!

    /**
     * 재가입 제한 기간이 지난 상태를 만들기 위해 withdrawnAt을 직접 변경한다.
     * 기간은 설정(RejoinPolicy)을 그대로 써서 yml만 바꿔도 테스트가 어긋나지 않게 한다.
     */
    private fun backdateWithdrawalPastRejoinBlock(userId: Long) {
        val withdrawnAt = LocalDateTime.now().minus(rejoinPolicy.blockedPeriod).minusDays(1)
        tx.execute {
            em
                .createQuery("update User u set u.withdrawnAt = :at where u.id = :id")
                .setParameter("at", withdrawnAt)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    /** 같은 이름과 Path의 쿠키가 Max-Age=0으로 내려오는지 확인한다. */
    private fun containsCookieExpiry() =
        allOf(
            containsString("${RefreshCookieFactory.REFRESH_COOKIE}="),
            containsString("Max-Age=0"),
        )
}
