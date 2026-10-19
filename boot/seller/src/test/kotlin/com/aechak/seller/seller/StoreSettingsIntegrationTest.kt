package com.aechak.seller.seller

import com.aechak.api.support.FakeFileStorage
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.seller.application.SellerApplication
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.application.enums.BusinessType
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 셀러 스토어 설정 조회·수정 API 통합 계약. HTTP 경계부터 실 MySQL까지 태워 응답 모양과 거절 코드를 고정한다.
 * 한 화면이 입점 신청과 스토어 운영을 겸하므로, 조회가 두 경우를 모두 표현하는지도 여기서 본다 —
 * 깨지면 승인 전 셀러가 신청 화면을 잃거나, 승격되지 않은 이미지 키가 스토어에 저장된 것이다.
 */
class StoreSettingsIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var fileStorage: FileStorage

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        openSeller(sellerUserId)
        clearPromoted()
    }

    @Test
    fun `승인 셀러의 조회는 스토어 정보와 승인된 신청 상태를 함께 내려준다`() {
        seedApplication(sellerUserId, ApplicationStatus.APPROVED)

        mockMvc
            .perform(getRequest(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isSeller").value(true))
            .andExpect(jsonPath("$.data.applicationStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.store.sellerId").value(sellerUserId))
            .andExpect(jsonPath("$.data.store.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.store.storeName").value("애착상회$sellerUserId"))
            .andExpect(jsonPath("$.data.store.brandIntro").value(null))
            .andExpect(jsonPath("$.data.store.profileImageUrl").value(null))
            .andExpect(jsonPath("$.data.store.profileImageKey").value(null))
            .andExpect(jsonPath("$.data.store.bannerImageUrl").value(null))
            .andExpect(jsonPath("$.data.store.bannerImageKey").value(null))
            .andExpect(jsonPath("$.data.store.confirmedOrderCount").value(0))
            .andExpect(jsonPath("$.data.store.telesalesNoticeStatus").value("NOT_REQUIRED"))
    }

    @Test
    fun `심사 중인 신청자의 조회는 200으로 신청 상태만 내려준다`() {
        val applicantId = createActiveUser()
        seedApplication(applicantId, ApplicationStatus.SUBMITTED)

        mockMvc
            .perform(getRequest(mintAccessToken(applicantId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isSeller").value(false))
            .andExpect(jsonPath("$.data.applicationStatus").value("SUBMITTED"))
            .andExpect(jsonPath("$.data.store").value(null))
    }

    @Test
    fun `신청 이력이 없는 계정의 조회는 신청 상태도 비워 내려준다`() {
        mockMvc
            .perform(getRequest(mintAccessToken(createActiveUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isSeller").value(false))
            .andExpect(jsonPath("$.data.applicationStatus").value(null))
            .andExpect(jsonPath("$.data.store").value(null))
    }

    @Test
    fun `저장한 스토어 설정이 그대로 다시 조회된다`() {
        mockMvc.perform(putRequest(token, storeJson())).andExpect(status().isNoContent)

        mockMvc
            .perform(getRequest(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.store.storeName").value("애착스토어"))
            .andExpect(jsonPath("$.data.store.brandIntro").value("반려동물 수제 간식을 만듭니다."))
            .andExpect(jsonPath("$.data.store.zipCode").value("06236"))
            .andExpect(jsonPath("$.data.store.roadAddress").value("서울 강남구 테헤란로 1"))
            .andExpect(jsonPath("$.data.store.detailAddress").value("3층"))
    }

    @Test
    fun `tmp 이미지 키는 승격된 키로 저장되고 공개 URL이 조립된다`() {
        mockMvc
            .perform(putRequest(token, storeJson(profileImageKey = tmpKey("p.webp"), bannerImageKey = tmpKey("b.webp"))))
            .andExpect(status().isNoContent)

        assertEquals(
            listOf("sellers/store/p.webp", "sellers/store/b.webp"),
            promotedKeys(),
            "tmp 키는 상품 이미지와 같은 승격 경로를 거친다",
        )
        mockMvc
            .perform(getRequest(token))
            .andExpect(jsonPath("$.data.store.profileImageKey").value("sellers/store/p.webp"))
            .andExpect(jsonPath("$.data.store.profileImageUrl").value("https://fake-cdn.local/sellers/store/p.webp"))
            .andExpect(jsonPath("$.data.store.bannerImageKey").value("sellers/store/b.webp"))
            .andExpect(jsonPath("$.data.store.bannerImageUrl").value("https://fake-cdn.local/sellers/store/b.webp"))
    }

    @Test
    fun `이미 저장된 키를 다시 보내면 승격을 건너뛴다`() {
        mockMvc
            .perform(putRequest(token, storeJson(profileImageKey = tmpKey("p.webp"))))
            .andExpect(status().isNoContent)
        clearPromoted()

        mockMvc
            .perform(putRequest(token, storeJson(profileImageKey = "sellers/store/p.webp")))
            .andExpect(status().isNoContent)

        assertTrue(promotedKeys().isEmpty(), "저장값 재전송은 승격 대상이 아니다 — 스킵은 필수")
        assertEquals("sellers/store/p.webp", loadSeller(sellerUserId).profileImageKey)
    }

    @Test
    fun `이미지를 교체하면 새 키로 바뀌고 이전 키는 스토리지에서 지우지 않는다`() {
        mockMvc
            .perform(putRequest(token, storeJson(profileImageKey = tmpKey("old.webp"))))
            .andExpect(status().isNoContent)
        clearPromoted()

        mockMvc
            .perform(putRequest(token, storeJson(profileImageKey = tmpKey("new.webp"))))
            .andExpect(status().isNoContent)

        assertEquals("sellers/store/new.webp", loadSeller(sellerUserId).profileImageKey)
        assertEquals(listOf("sellers/store/new.webp"), promotedKeys())
        assertTrue("sellers/store/old.webp" !in deletedKeys(), "교체된 이전 키는 S3 고아로 남긴다 — 정리 배치는 후속")
    }

    @Test
    fun `교체로 떨어져 나간 이전 키는 다시 저장할 수 없다`() {
        mockMvc
            .perform(putRequest(token, storeJson(profileImageKey = tmpKey("old.webp"))))
            .andExpect(status().isNoContent)
        mockMvc
            .perform(putRequest(token, storeJson(profileImageKey = tmpKey("new.webp"))))
            .andExpect(status().isNoContent)

        // 저장값이 아니게 된 키는 새 업로드로 취급되고, 승격 경로는 tmp 키만 받는다
        mockMvc
            .perform(putRequest(token, storeJson(profileImageKey = "sellers/store/old.webp")))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(FileErrorCode.FILE_ACCESS_DENIED.code))
        assertEquals("sellers/store/new.webp", loadSeller(sellerUserId).profileImageKey)
    }

    @Test
    fun `이미지를 비워 보내면 저장된 키가 제거된다`() {
        mockMvc
            .perform(putRequest(token, storeJson(profileImageKey = tmpKey("p.webp"), bannerImageKey = tmpKey("b.webp"))))
            .andExpect(status().isNoContent)

        mockMvc.perform(putRequest(token, storeJson())).andExpect(status().isNoContent)

        val seller = loadSeller(sellerUserId)
        assertNull(seller.profileImageKey, "비워 보낸 프로필 이미지가 남아 있다")
        assertNull(seller.bannerImageKey, "비워 보낸 배너 이미지가 남아 있다")
    }

    @Test
    fun `타인이 업로드한 tmp 키는 100002로 거절된다`() {
        val other = createActiveUser()
        mockMvc
            .perform(
                putRequest(
                    token,
                    storeJson(profileImageKey = "${FileKey.tmpPrefixOf(other, UploadPurpose.SELLER_STORE)}p.webp"),
                ),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(FileErrorCode.FILE_ACCESS_DENIED.code))
        assertEquals("애착상회$sellerUserId", loadSeller(sellerUserId).storeName, "거절된 요청이 상호를 바꿨다")
    }

    @Test
    fun `발급 용도가 다른 tmp 키는 100003으로 거절된다`() {
        mockMvc
            .perform(
                putRequest(
                    token,
                    storeJson(profileImageKey = "${FileKey.tmpPrefixOf(sellerUserId, UploadPurpose.PRODUCT)}p.webp"),
                ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(FileErrorCode.FILE_PURPOSE_MISMATCH.code))
    }

    @Test
    fun `상호가 공백이면 90001로 거절되고 값이 바뀌지 않는다`() {
        mockMvc
            .perform(putRequest(token, storeJson(storeName = "   ")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
        assertEquals("애착상회$sellerUserId", loadSeller(sellerUserId).storeName)
    }

    @Test
    fun `상호가 101자면 90001로 거절된다`() {
        mockMvc
            .perform(putRequest(token, storeJson(storeName = "가".repeat(Seller.MAX_STORE_NAME_LENGTH + 1))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `브랜드 소개가 1001자면 90001로 거절된다`() {
        mockMvc
            .perform(putRequest(token, storeJson(brandIntro = "가".repeat(Seller.MAX_BRAND_INTRO_LENGTH + 1))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `상호 앞뒤 공백은 떼고 저장된다`() {
        mockMvc
            .perform(putRequest(token, storeJson(storeName = "  애착스토어  ")))
            .andExpect(status().isNoContent)

        assertEquals("애착스토어", loadSeller(sellerUserId).storeName)
    }

    @Test
    fun `같은 상호를 쓰는 셀러가 둘 있을 수 있다`() {
        val other = createActiveUser()
        openSeller(other)
        mockMvc.perform(putRequest(token, storeJson(storeName = "같은이름상회"))).andExpect(status().isNoContent)

        mockMvc
            .perform(putRequest(mintAccessToken(other), storeJson(storeName = "같은이름상회")))
            .andExpect(status().isNoContent)

        assertEquals("같은이름상회", loadSeller(other).storeName, "상호 중복 정책은 아직 없다 — 생기면 이 테스트가 먼저 깨진다")
    }

    @Test
    fun `스토어 주소와 배송 정책 출고지는 같은 값이다`() {
        mockMvc.perform(putRequest(token, storeJson())).andExpect(status().isNoContent)

        mockMvc
            .perform(get("/api/v1/sellers/me/shipping-policy").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(jsonPath("$.data.originZipCode").value("06236"))
            .andExpect(jsonPath("$.data.originRoadAddress").value("서울 강남구 테헤란로 1"))

        mockMvc
            .perform(
                put("/api/v1/sellers/me/shipping-policy")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(shippingPolicyJson()),
            ).andExpect(status().isNoContent)

        mockMvc
            .perform(getRequest(token))
            .andExpect(jsonPath("$.data.store.zipCode").value("13529"))
            .andExpect(jsonPath("$.data.store.roadAddress").value("경기 성남시 판교역로 2"))
    }

    @Test
    fun `셀러가 아닌 계정의 저장은 404 10000이다`() {
        mockMvc
            .perform(putRequest(mintAccessToken(createActiveUser()), storeJson()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.SELLER_NOT_FOUND.code))
    }

    @Test
    fun `정지 셀러는 조회는 되고 저장은 403 10002다`() {
        suspendSeller(sellerUserId)

        mockMvc
            .perform(getRequest(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isSeller").value(true))
            .andExpect(jsonPath("$.data.store.status").value("SUSPENDED"))

        mockMvc
            .perform(putRequest(token, storeJson()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.SELLER_NOT_ACTIVE.code))
        assertEquals("애착상회$sellerUserId", loadSeller(sellerUserId).storeName)
    }

    @Test
    fun `타 셀러의 저장은 내 스토어를 바꾸지 않는다`() {
        val other = createActiveUser()
        openSeller(other)

        mockMvc
            .perform(putRequest(mintAccessToken(other), storeJson(storeName = "옆집상회")))
            .andExpect(status().isNoContent)

        assertEquals("애착상회$sellerUserId", loadSeller(sellerUserId).storeName)
    }

    @Test
    fun `미인증 요청은 401 20004다`() {
        mockMvc
            .perform(get("/api/v1/sellers/me/store"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    // ---------- 헬퍼 ----------

    private fun getRequest(token: String): MockHttpServletRequestBuilder =
        get("/api/v1/sellers/me/store").header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun putRequest(
        token: String,
        body: String,
    ): MockHttpServletRequestBuilder =
        put("/api/v1/sellers/me/store")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun storeJson(
        storeName: String = "애착스토어",
        brandIntro: String? = "반려동물 수제 간식을 만듭니다.",
        profileImageKey: String? = null,
        bannerImageKey: String? = null,
    ): String =
        """
        {"storeName": "$storeName", "brandIntro": ${brandIntro.quoted()},
         "profileImageKey": ${profileImageKey.quoted()}, "bannerImageKey": ${bannerImageKey.quoted()},
         "zipCode": "06236", "roadAddress": "서울 강남구 테헤란로 1", "detailAddress": "3층"}
        """.trimIndent()

    private fun shippingPolicyJson(): String =
        """
        {"defaultCourierCode": null, "baseShippingFee": 3000, "freeShippingThreshold": null,
         "originZipCode": "13529", "originRoadAddress": "경기 성남시 판교역로 2", "originDetailAddress": "물류센터",
         "returnSameAsOrigin": true,
         "returnZipCode": null, "returnRoadAddress": null, "returnDetailAddress": null,
         "jejuExtraFee": 0, "remoteAreaExtraFee": 0, "returnFee": 0, "exchangeFee": 0,
         "avgDispatchDays": 2, "shippingPolicyText": null}
        """.trimIndent()

    private fun String?.quoted(): String = if (this == null) "null" else "\"$this\""

    private fun tmpKey(fileName: String): String = "${FileKey.tmpPrefixOf(sellerUserId, UploadPurpose.SELLER_STORE)}$fileName"

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 3000L)) }
    }

    /** 신청서는 상태만 쓰므로 전이 메서드로 목표 상태까지 밀어 둔다. */
    private fun seedApplication(
        userId: Long,
        status: ApplicationStatus,
    ) {
        tx.execute {
            val application = SellerApplication.draft(userId, BusinessType.PERSONAL_GENERAL)
            if (status != ApplicationStatus.DRAFT) {
                application.submit()
            }
            if (status == ApplicationStatus.APPROVED) {
                application.approve(1L)
            }
            em.persist(application)
        }
    }

    private fun suspendSeller(userId: Long) {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :status where s.userId = :userId")
                .setParameter("status", SellerStatus.SUSPENDED)
                .setParameter("userId", userId)
                .executeUpdate()
        }
    }

    /** 1차 캐시 가짜 통과를 막으려고 새 트랜잭션에서 다시 읽는다. */
    private fun loadSeller(userId: Long): Seller = tx.execute { em.find(Seller::class.java, userId) }!!

    private fun promotedKeys(): List<String> = (fileStorage as FakeFileStorage).promotedKeys

    private fun deletedKeys(): List<String> = (fileStorage as FakeFileStorage).deletedKeys

    private fun clearPromoted() = (fileStorage as FakeFileStorage).clearPromoted()
}
