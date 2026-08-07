package com.aechak.api.seller

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.seller.Seller
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 입점 신청 저장(폼+서류 일괄)·현황 조회·제출 통합 테스트 (실 MySQL — UNIQUE(user_id) 번역까지 실제 제약으로 검증).
 * 휴대폰 인증 API는 별도 브랜치라 인증 상태는 JPQL로 직접 시딩한다.
 */
class SellerApplicationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private var userId = 0L
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        userId = createOnboardedUser("셀러후보", phoneVerified = true)
        token = mintAccessToken(userId)
    }

    @Test
    fun `휴대폰 인증 없이 신청하면 10103을 반환한다`() {
        val unverified = createOnboardedUser("미인증유저", phoneVerified = false)
        mockMvc
            .perform(saveDraftRequest(mintAccessToken(unverified), applicationJson()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.PHONE_VERIFICATION_REQUIRED.code))
    }

    @Test
    fun `이미 셀러인 계정의 신청은 10001을 반환한다`() {
        tx.execute { em.persist(Seller.open(userId, "기존가게", 0L)) }
        mockMvc
            .perform(saveDraftRequest(token, applicationJson()))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.ALREADY_SELLER.code))
    }

    @Test
    fun `최초 신청은 DRAFT로 생성되고 계좌번호는 마스킹되어 내려온다`() {
        mockMvc
            .perform(saveDraftRequest(token, applicationJson(accountNumber = "110123456789")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.accountNumberMasked").value("********6789"))
            .andExpect(jsonPath("$.data.applicationId").exists())
    }

    @Test
    fun `유형만 보내도 임시저장된다 - 필수성 검증은 제출로 일원화`() {
        mockMvc
            .perform(saveDraftRequest(token, """{"businessType": "PERSONAL_GENERAL"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.representativeName").value(nullValue()))
            .andExpect(jsonPath("$.data.accountNumberMasked").value(nullValue()))
    }

    @Test
    fun `DRAFT 재호출은 같은 신청서를 수정한다`() {
        val first = performForBody(saveDraftRequest(token, applicationJson(representativeName = "김운경")))
        val second = performForBody(saveDraftRequest(token, applicationJson(representativeName = "김수정")))
        assertEquals(readLong(first, "applicationId"), readLong(second, "applicationId"), "행 재사용 모델 — 신청서는 유저당 1행")
        assertTrue(second.contains("\"representativeName\":\"김수정\""))
    }

    @Test
    fun `SUBMITTED 상태의 수정은 10101을 반환한다`() {
        performForBody(saveDraftRequest(token, applicationJson()))
        submitDirectly(userId)
        mockMvc
            .perform(saveDraftRequest(token, applicationJson()))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED.code))
    }

    @Test
    fun `REJECTED 신청의 재작성은 DRAFT로 복귀한다`() {
        performForBody(saveDraftRequest(token, applicationJson()))
        submitDirectly(userId)
        rejectDirectly(userId, "서류 불선명")
        mockMvc
            .perform(saveDraftRequest(token, applicationJson(representativeName = "재도전")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.representativeName").value("재도전"))
    }

    @Test
    fun `동시 최초 신청은 한쪽이 10104로 번역된다`() {
        // 스레드 A가 INSERT 후 커밋을 늦추는 동안 B의 INSERT는 UNIQUE(user_id) 잠금에 걸렸다가 1062로 터진다.
        val inserted = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val slowInsert =
            executor.submit {
                tx.execute {
                    em
                        .createNativeQuery(
                            "insert into seller_applications (user_id, business_type, status, version, created_at, updated_at) " +
                                "values (:userId, 'PERSONAL_GENERAL', 'DRAFT', 0, now(), now())",
                        ).setParameter("userId", userId)
                        .executeUpdate()
                    inserted.countDown()
                    Thread.sleep(500) // B가 잠금 대기에 들어갈 시간
                }
            }
        assertTrue(inserted.await(5, TimeUnit.SECONDS))
        mockMvc
            .perform(saveDraftRequest(token, applicationJson()))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.APPLICATION_ALREADY_IN_PROGRESS.code))
        slowInsert.get(5, TimeUnit.SECONDS)
        executor.shutdown()
    }

    @Test
    fun `신청 이력이 없으면 현황 조회는 10100을 반환한다`() {
        mockMvc
            .perform(get("/api/v1/seller-applications/me").bearer(token))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.SELLER_APPLICATION_NOT_FOUND.code))
    }

    @Test
    fun `현황 조회는 저장된 신청서를 계좌 마스킹과 함께 반환한다`() {
        performForBody(saveDraftRequest(token, applicationJson(accountNumber = "3521234567")))
        mockMvc
            .perform(get("/api/v1/seller-applications/me").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.accountNumberMasked").value("******4567"))
    }

    @Test
    fun `서류를 함께 저장하면 현황 조회에 종류가 노출된다`() {
        performForBody(saveDraftRequest(token, applicationJson(documents = docs("ID_CARD"))))
        mockMvc
            .perform(get("/api/v1/seller-applications/me").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.documents.length()").value(1))
            .andExpect(jsonPath("$.data.documents[0].documentType").value("ID_CARD"))
    }

    @Test
    fun `같은 종류 서류를 다시 저장하면 교체된다`() {
        performForBody(saveDraftRequest(token, applicationJson(documents = listOf("ID_CARD" to tmpKey(userId, "id-old.png")))))
        performForBody(saveDraftRequest(token, applicationJson(documents = listOf("ID_CARD" to tmpKey(userId, "id-new.png")))))
        mockMvc
            .perform(get("/api/v1/seller-applications/me").bearer(token))
            .andExpect(jsonPath("$.data.documents.length()").value(1))
    }

    @Test
    fun `서류 없이 재저장해도 기존 서류는 유지된다 - 부분 upsert`() {
        performForBody(saveDraftRequest(token, applicationJson(documents = docs("ID_CARD", "BANKBOOK_COPY"))))
        performForBody(saveDraftRequest(token, applicationJson(representativeName = "폼만수정")))
        mockMvc
            .perform(get("/api/v1/seller-applications/me").bearer(token))
            .andExpect(jsonPath("$.data.documents.length()").value(2))
            .andExpect(jsonPath("$.data.representativeName").value("폼만수정"))
    }

    @Test
    fun `한 요청에 같은 종류 서류가 중복이면 형식 위반으로 거절된다`() {
        val body = applicationJson(documents = listOf("ID_CARD" to tmpKey(userId, "a.png"), "ID_CARD" to tmpKey(userId, "b.png")))
        mockMvc
            .perform(saveDraftRequest(token, body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `SUBMITTED 신청에는 서류를 저장할 수 없다`() {
        performForBody(saveDraftRequest(token, applicationJson()))
        submitDirectly(userId)
        mockMvc
            .perform(saveDraftRequest(token, applicationJson(documents = docs("ID_CARD"))))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED.code))
    }

    @Test
    fun `타인이 업로드한 tmpKey는 100002로 거절된다`() {
        val other = createOnboardedUser("타인업로더", phoneVerified = true)
        mockMvc
            .perform(saveDraftRequest(token, applicationJson(documents = listOf("ID_CARD" to tmpKey(other, "id.png")))))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(FileErrorCode.FILE_ACCESS_DENIED.code))
    }

    @Test
    fun `발급 용도가 다른 tmpKey는 100003으로 거절된다`() {
        val profileKey = "${FileKey.tmpPrefixOf(userId, UploadPurpose.USER_PROFILE)}avatar.png"
        mockMvc
            .perform(saveDraftRequest(token, applicationJson(documents = listOf("ID_CARD" to profileKey))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(FileErrorCode.FILE_PURPOSE_MISMATCH.code))
    }

    @Test
    fun `개인 일반 유형은 신분증·통장 사본이 갖춰지면 제출된다`() {
        performForBody(saveDraftRequest(token, applicationJson(documents = docs("ID_CARD", "BANKBOOK_COPY"))))
        mockMvc
            .perform(submitRequest(token))
            .andExpect(status().isNoContent)
        mockMvc
            .perform(get("/api/v1/seller-applications/me").bearer(token))
            .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
    }

    @Test
    fun `개인사업자 유형은 사업자 정보·서류까지 갖춰야 제출된다`() {
        performForBody(
            saveDraftRequest(
                token,
                applicationJson(
                    businessType = "SOLE_PROPRIETORSHIP",
                    businessName = "애착상회",
                    businessRegNo = "1234567891",
                    documents = docs("ID_CARD", "BANKBOOK_COPY", "BUSINESS_REGISTRATION", "TELESALES_REPORT"),
                ),
            ),
        )
        mockMvc
            .perform(submitRequest(token))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `법인 유형은 6종 서류와 법인등록번호까지 갖춰야 제출된다`() {
        performForBody(
            saveDraftRequest(
                token,
                applicationJson(
                    businessType = "CORPORATE",
                    businessName = "애착주식회사",
                    businessRegNo = "1234567891",
                    corpRegNo = "110111-1234567",
                    documents =
                        docs(
                            "ID_CARD",
                            "BANKBOOK_COPY",
                            "BUSINESS_REGISTRATION",
                            "TELESALES_REPORT",
                            "CORP_REGISTER",
                            "CORP_SEAL",
                        ),
                ),
            ),
        )
        mockMvc
            .perform(submitRequest(token))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `필수 서류가 빠지면 10105와 부족 목록을 반환한다`() {
        performForBody(saveDraftRequest(token, applicationJson(documents = docs("ID_CARD"))))
        val body =
            mockMvc
                .perform(submitRequest(token))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.REQUIRED_DOCUMENTS_MISSING.code))
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        assertTrue(body.contains("통장 사본"), "부족 서류 라벨이 메시지에 실려야 한다: $body")
    }

    @Test
    fun `법인 필수 정보가 빠지면 10105와 부족 항목을 반환한다`() {
        performForBody(
            saveDraftRequest(
                token,
                applicationJson(
                    businessType = "CORPORATE",
                    businessName = "애착주식회사",
                    businessRegNo = "1234567891",
                    documents =
                        docs(
                            "ID_CARD",
                            "BANKBOOK_COPY",
                            "BUSINESS_REGISTRATION",
                            "TELESALES_REPORT",
                            "CORP_REGISTER",
                            "CORP_SEAL",
                        ),
                ),
            ),
        )
        val body =
            mockMvc
                .perform(submitRequest(token))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.REQUIRED_DOCUMENTS_MISSING.code))
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        assertTrue(body.contains("법인등록번호"), "부족 정보 라벨이 메시지에 실려야 한다: $body")
    }

    @Test
    fun `제출할 신청이 없으면 10100을 반환한다`() {
        mockMvc
            .perform(submitRequest(token))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.SELLER_APPLICATION_NOT_FOUND.code))
    }

    @Test
    fun `반려 후 재작성하면 기존 서류를 유지한 채 재제출된다`() {
        performForBody(saveDraftRequest(token, applicationJson(documents = docs("ID_CARD", "BANKBOOK_COPY"))))
        mockMvc.perform(submitRequest(token)).andExpect(status().isNoContent)
        rejectDirectly(userId, "신분증 불선명")
        mockMvc
            .perform(saveDraftRequest(token, applicationJson(representativeName = "재도전")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.documents.length()").value(2))
        mockMvc
            .perform(submitRequest(token))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `SUBMITTED 신청의 재제출은 10101을 반환한다`() {
        performForBody(saveDraftRequest(token, applicationJson(documents = docs("ID_CARD", "BANKBOOK_COPY"))))
        mockMvc.perform(submitRequest(token)).andExpect(status().isNoContent)
        mockMvc
            .perform(submitRequest(token))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED.code))
    }

    @Test
    fun `사업자등록번호 체크섬이 틀리면 90001로 거절된다`() {
        mockMvc
            .perform(saveDraftRequest(token, applicationJson(businessRegNo = "1234567890")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    /**
     * getMe(신청 전제 검증)가 프로필을 요구해 온보딩 경유로 심는다 — 베이스 createActiveUser는 프로필이 없다.
     * 휴대폰 인증 API는 별도 브랜치라 인증 상태는 JPQL로 직접 시딩한다.
     */
    private fun createOnboardedUser(
        nickname: String,
        phoneVerified: Boolean,
    ): Long {
        val id =
            tx.execute {
                val user =
                    com.aechak.domain.user.user.User
                        .preRegister()
                user.completeOnboarding(nickname)
                em.persist(user)
                user.id
            }!!
        if (phoneVerified) {
            tx.execute {
                em
                    .createQuery("update User u set u.isPhoneVerified = true where u.id = :id")
                    .setParameter("id", id)
                    .executeUpdate()
            }
        }
        return id
    }

    private fun submitDirectly(ownerId: Long) {
        tx.execute {
            em
                .createQuery("select a from SellerApplication a where a.userId = :userId")
                .setParameter("userId", ownerId)
                .resultList
                .filterIsInstance<com.aechak.domain.seller.application.SellerApplication>()
                .first()
                .submit()
        }
    }

    private fun rejectDirectly(
        ownerId: Long,
        reason: String,
    ) {
        tx.execute {
            em
                .createQuery("select a from SellerApplication a where a.userId = :userId")
                .setParameter("userId", ownerId)
                .resultList
                .filterIsInstance<com.aechak.domain.seller.application.SellerApplication>()
                .first()
                .reject(reviewerAdminId = 999L, reason = reason)
        }
    }

    private fun tmpKey(
        ownerId: Long,
        fileName: String,
    ): String = "${FileKey.tmpPrefixOf(ownerId, UploadPurpose.SELLER_DOCUMENT)}$fileName"

    /** 종류별 tmpKey 페어 — 내(userId) 업로드분. */
    private fun docs(vararg documentTypes: String): List<Pair<String, String>> = documentTypes.map { it to tmpKey(userId, "$it.png") }

    private fun saveDraftRequest(
        token: String,
        body: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/seller-applications")
            .bearer(token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun submitRequest(token: String): MockHttpServletRequestBuilder = post("/api/v1/seller-applications/me/submit").bearer(token)

    private fun performForBody(request: MockHttpServletRequestBuilder): String =
        mockMvc
            .perform(request)
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun readLong(
        body: String,
        field: String,
    ): Long = Regex("\"$field\":(\\d+)").find(body)!!.groupValues[1].toLong()

    private fun applicationJson(
        businessType: String = "PERSONAL_GENERAL",
        representativeName: String = "김운경",
        accountNumber: String = "110123456789",
        businessRegNo: String? = null,
        businessName: String? = null,
        corpRegNo: String? = null,
        documents: List<Pair<String, String>> = emptyList(),
    ): String {
        val optional =
            listOfNotNull(
                businessRegNo?.let { "\"businessRegNo\": \"$it\"," },
                businessName?.let { "\"businessName\": \"$it\"," },
                corpRegNo?.let { "\"corpRegNo\": \"$it\"," },
            ).joinToString("\n")
        val documentsJson =
            documents.joinToString(", ", prefix = "[", postfix = "]") {
                """{"documentType": "${it.first}", "tmpKey": "${it.second}"}"""
            }
        return """
            {
              "businessType": "$businessType",
              $optional
              "representativeName": "$representativeName",
              "bankCode": "004",
              "accountNumber": "$accountNumber",
              "accountHolder": "$representativeName",
              "documents": $documentsJson
            }
            """.trimIndent()
    }

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
}
