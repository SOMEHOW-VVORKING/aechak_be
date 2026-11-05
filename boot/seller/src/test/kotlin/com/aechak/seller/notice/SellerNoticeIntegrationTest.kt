package com.aechak.seller.notice

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.notice.notice.Notice
import com.aechak.domain.notice.notice.enums.NoticeAudience
import com.aechak.domain.notice.notice.enums.NoticeCategory
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

/**
 * 셀러센터 공지 조회 통합 계약.
 *
 * 지키려는 경계가 둘이다 — (1) 셀러는 전체·셀러 대상 공지만 보고 구매자 대상 공지는 못 본다,
 * (2) 게시되지 않은 공지(초안·내림)는 아무에게도 안 보인다. 둘 중 하나가 풀리면 운영자가
 * 쓰는 중인 초안이나 다른 대상용 공지가 셀러에게 나간다.
 *
 * 내부 메모가 응답에 섞이지 않는지도 함께 못 박는다 — 운영자끼리 쓴 메모가 셀러에게 나가면
 * 사고다. 필드가 없는 것만으로는 부족해서 본문 전체에 메모 문자열이 없는지까지 본다.
 */
class SellerNoticeIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

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
    }

    // --- 권한 경계 ---

    @Test
    fun `토큰이 없으면 401과 20004다`() {
        mockMvc
            .perform(get(BASE))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `셀러로 등록되지 않은 계정은 403과 10004다`() {
        val notSeller = mintAccessToken(createActiveUser())

        request(asToken = notSeller)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.NOT_SELLER.code))
    }

    /** 상세도 같은 게이트를 지난다 — 자격 판정이 공지 조회보다 앞서야 404로 존재 여부가 새지 않는다. */
    @Test
    fun `셀러가 아닌 계정의 상세 요청은 공지가 있어도 403과 10004다`() {
        val publicId = seedNotice(audience = NoticeAudience.ALL, published = true)
        val notSeller = mintAccessToken(createActiveUser())

        mockMvc
            .perform(get("$BASE/$publicId").header(HttpHeaders.AUTHORIZATION, "Bearer $notSeller"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(SellerErrorCode.NOT_SELLER.code))
    }

    /** 제재는 자격 소멸이 아니다 — 왜 정지됐고 무엇이 바뀌는지 읽을 길을 막으면 셀러가 대응할 수 없다. */
    @Test
    fun `정지된 셀러도 공지를 읽는다`() {
        val suspendedUserId = createActiveUser()
        openSeller(suspendedUserId)
        suspendSeller(suspendedUserId)
        seedNotice(audience = NoticeAudience.SELLER, published = true)

        request(asToken = mintAccessToken(suspendedUserId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(1))
    }

    // --- 노출 대상 ---

    @Test
    fun `전체 대상과 셀러 대상 공지만 보이고 구매자 대상은 보이지 않는다`() {
        val all = seedNotice(title = "전체 공지", audience = NoticeAudience.ALL, published = true)
        val seller = seedNotice(title = "셀러 공지", audience = NoticeAudience.SELLER, published = true)
        seedNotice(title = "구매자 공지", audience = NoticeAudience.BUYER, published = true)

        request()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.items[*].noticeId").value(org.hamcrest.Matchers.containsInAnyOrder(seller, all)))
    }

    @Test
    fun `구매자 대상 공지의 상세는 404와 150000이다`() {
        val buyerOnly = seedNotice(audience = NoticeAudience.BUYER, published = true)

        mockMvc
            .perform(get("$BASE/$buyerOnly").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(150000))
    }

    @Test
    fun `초안과 내린 공지는 목록에도 상세에도 없다`() {
        val draft = seedNotice(title = "초안", audience = NoticeAudience.SELLER, published = false)
        val unpublished = seedNotice(title = "내림", audience = NoticeAudience.SELLER, published = true, thenUnpublish = true)
        seedNotice(title = "게시 중", audience = NoticeAudience.SELLER, published = true)

        request()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.items[0].title").value("게시 중"))

        listOf(draft, unpublished).forEach { publicId ->
            mockMvc
                .perform(get("$BASE/$publicId").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(150000))
        }
    }

    // --- 응답 계약 ---

    @Test
    fun `내부 메모는 목록에도 상세에도 실리지 않는다`() {
        val publicId =
            seedNotice(
                audience = NoticeAudience.SELLER,
                published = true,
                internalMemo = SECRET_MEMO,
            )

        val listBody =
            request()
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        val detailBody =
            mockMvc
                .perform(get("$BASE/$publicId").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.internalMemo").doesNotExist())
                .andExpect(jsonPath("$.data.audience").doesNotExist())
                .andReturn()
                .response
                .contentAsString

        org.junit.jupiter.api.Assertions
            .assertFalse(listBody.contains(SECRET_MEMO), "목록 응답에 내부 메모가 섞였다: $listBody")
        org.junit.jupiter.api.Assertions
            .assertFalse(detailBody.contains(SECRET_MEMO), "상세 응답에 내부 메모가 섞였다: $detailBody")
    }

    @Test
    fun `목록은 본문 앞머리만 주고 상세는 전문을 준다`() {
        val longContent = "가".repeat(150)
        val publicId = seedNotice(audience = NoticeAudience.SELLER, published = true, content = longContent)

        request()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].contentPreview").value("가".repeat(100) + "…"))
            .andExpect(jsonPath("$.data.items[0].content").doesNotExist())

        mockMvc
            .perform(get("$BASE/$publicId").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content").value(longContent))
    }

    // --- 정렬·필터·페이징 ---

    @Test
    fun `고정 공지가 최근 공지보다 먼저 온다`() {
        seedNotice(title = "최신", audience = NoticeAudience.SELLER, published = true, publishedAt = NOW)
        val pinned =
            seedNotice(
                title = "고정",
                audience = NoticeAudience.SELLER,
                published = true,
                pinned = true,
                publishedAt = NOW.minusDays(30),
            )

        request()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].noticeId").value(pinned))
            .andExpect(jsonPath("$.data.items[0].pinned").value(true))
            .andExpect(jsonPath("$.data.items[1].title").value("최신"))
    }

    @Test
    fun `분류 필터는 그 분류의 공지만 준다`() {
        val settlement =
            seedNotice(audience = NoticeAudience.SELLER, published = true, category = NoticeCategory.SETTLEMENT)
        seedNotice(audience = NoticeAudience.SELLER, published = true, category = NoticeCategory.EVENT)

        request(category = "SETTLEMENT")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.items[0].noticeId").value(settlement))
    }

    /**
     * 커서가 고정 여부를 싣지 않으면 여기서 깨진다 — 고정 공지에서 끊긴 페이지의 다음 장이
     * 게시 시각 비교만 남아, 고정보다 최근에 게시된 일반 공지를 전부 건너뛴다.
     */
    @Test
    fun `고정 공지에서 끊긴 커서가 최근 일반 공지를 건너뛰지 않는다`() {
        val pinned =
            seedNotice(
                title = "고정",
                audience = NoticeAudience.SELLER,
                published = true,
                pinned = true,
                publishedAt = NOW.minusDays(30),
            )
        val recent = seedNotice(title = "최신", audience = NoticeAudience.SELLER, published = true, publishedAt = NOW)
        val older =
            seedNotice(title = "예전", audience = NoticeAudience.SELLER, published = true, publishedAt = NOW.minusDays(60))

        val firstPage =
            request(size = "1")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items[0].noticeId").value(pinned))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn()
                .response
                .contentAsString

        val secondPage =
            request(size = "1", cursor = JsonPath.read(firstPage, "$.data.nextCursor"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items[0].noticeId").value(recent))
                .andExpect(jsonPath("$.data.totalCount").doesNotExist())
                .andReturn()
                .response
                .contentAsString

        request(size = "1", cursor = JsonPath.read(secondPage, "$.data.nextCursor"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].noticeId").value(older))
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
    }

    @Test
    fun `망가진 커서는 400과 90002다`() {
        request(cursor = "!!broken!!")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    // --- 헬퍼 ---

    private fun request(
        asToken: String = token,
        category: String? = null,
        cursor: String? = null,
        size: String? = null,
    ): ResultActions {
        val builder = get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer $asToken")
        category?.let { builder.param("category", it) }
        cursor?.let { builder.param("cursor", it) }
        size?.let { builder.param("size", it) }
        return mockMvc.perform(builder)
    }

    private fun seedNotice(
        title: String = "셀러 운영 정책 안내",
        content: String = "11월 1일부터 반품 배송비 부담 기준이 바뀌어요.",
        category: NoticeCategory = NoticeCategory.POLICY,
        audience: NoticeAudience = NoticeAudience.ALL,
        pinned: Boolean = false,
        internalMemo: String? = null,
        published: Boolean,
        thenUnpublish: Boolean = false,
        publishedAt: LocalDateTime = NOW,
    ): String =
        tx.execute {
            val notice = Notice.draft(ADMIN_ID, title, content, category, audience, pinned, internalMemo)
            if (published) {
                notice.publish(ADMIN_ID, publishedAt)
                if (thenUnpublish) {
                    notice.unpublish(ADMIN_ID)
                }
            }
            em.persist(notice)
            em.flush()
            notice.publicId
        }!!

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 3000L)) }
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

    private companion object {
        const val BASE = "/api/v1/sellers/me/notices"
        const val ADMIN_ID = 9L
        const val SECRET_MEMO = "법무 검토 전 문구 — 외부 노출 금지"
        val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 26, 10, 0)
    }
}
