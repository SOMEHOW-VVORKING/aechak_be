package com.aechak.api.notice

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.notice.notice.Notice
import com.aechak.domain.notice.notice.enums.NoticeAudience
import com.aechak.domain.notice.notice.enums.NoticeCategory
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Assertions.assertFalse
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
 * 구매자 공지 조회 통합 계약.
 *
 * 노출 대상을 가르는 축이 이 경로의 유일한 방어선이다 — 구매자가 셀러 대상 공지를 보면
 * 운영 정책·정산 안내가 엉뚱한 쪽으로 나간다. 내린 공지·초안이 안 보이는지도 함께 못 박는다.
 */
class NoticeIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        token = mintAccessToken(createActiveUser())
    }

    @Test
    fun `토큰이 없으면 401과 20004다`() {
        mockMvc
            .perform(get(BASE))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `전체 대상과 구매자 대상 공지만 보이고 셀러 대상은 보이지 않는다`() {
        val all = seedNotice(title = "전체 공지", audience = NoticeAudience.ALL)
        val buyer = seedNotice(title = "구매자 공지", audience = NoticeAudience.BUYER)
        seedNotice(title = "셀러 공지", audience = NoticeAudience.SELLER)

        request()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.items[*].noticeId").value(containsInAnyOrder(buyer, all)))
    }

    @Test
    fun `셀러 대상 공지의 상세는 404와 150000이다`() {
        val sellerOnly = seedNotice(audience = NoticeAudience.SELLER)

        mockMvc
            .perform(get("$BASE/$sellerOnly").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(150000))
    }

    @Test
    fun `초안과 내린 공지는 목록에도 상세에도 없다`() {
        val draft = seedNotice(title = "초안", audience = NoticeAudience.ALL, published = false)
        val unpublished = seedNotice(title = "내림", audience = NoticeAudience.ALL, thenUnpublish = true)
        seedNotice(title = "게시 중", audience = NoticeAudience.ALL)

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

    @Test
    fun `내부 메모는 어느 응답에도 실리지 않는다`() {
        val publicId = seedNotice(audience = NoticeAudience.BUYER, internalMemo = SECRET_MEMO)

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
                .andReturn()
                .response
                .contentAsString

        assertFalse(listBody.contains(SECRET_MEMO), "목록 응답에 내부 메모가 섞였다: $listBody")
        assertFalse(detailBody.contains(SECRET_MEMO), "상세 응답에 내부 메모가 섞였다: $detailBody")
    }

    @Test
    fun `고정 공지가 최근 공지보다 먼저 온다`() {
        seedNotice(title = "최신", audience = NoticeAudience.ALL, publishedAt = NOW)
        val pinned = seedNotice(title = "고정", audience = NoticeAudience.ALL, pinned = true, publishedAt = NOW.minusDays(30))

        request()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].noticeId").value(pinned))
            .andExpect(jsonPath("$.data.items[1].title").value("최신"))
    }

    @Test
    fun `분류 필터는 그 분류의 공지만 준다`() {
        val event = seedNotice(audience = NoticeAudience.BUYER, category = NoticeCategory.EVENT)
        seedNotice(audience = NoticeAudience.BUYER, category = NoticeCategory.POLICY)

        request(category = "EVENT")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.items[0].noticeId").value(event))
    }

    @Test
    fun `망가진 커서는 400과 90002다`() {
        request(cursor = "!!broken!!")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    private fun request(
        category: String? = null,
        cursor: String? = null,
    ): ResultActions {
        val builder = get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        category?.let { builder.param("category", it) }
        cursor?.let { builder.param("cursor", it) }
        return mockMvc.perform(builder)
    }

    private fun seedNotice(
        title: String = "구매 확정 자동 처리 기준이 바뀌어요",
        content: String = "배송 완료 후 7일이 지나면 구매가 자동 확정돼요.",
        category: NoticeCategory = NoticeCategory.POLICY,
        audience: NoticeAudience = NoticeAudience.ALL,
        pinned: Boolean = false,
        internalMemo: String? = null,
        published: Boolean = true,
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

    private companion object {
        const val BASE = "/api/v1/notices"
        const val ADMIN_ID = 9L
        const val SECRET_MEMO = "법무 검토 전 문구 — 외부 노출 금지"
        val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 24, 10, 0)
    }
}
