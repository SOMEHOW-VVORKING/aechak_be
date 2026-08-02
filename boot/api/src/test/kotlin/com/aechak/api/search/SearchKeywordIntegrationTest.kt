package com.aechak.api.search

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.search.keyword.RecommendedKeyword
import com.aechak.domain.search.recent.RecentSearch
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

/**
 * 검색어 조회 API(GET /search/keywords) 통합 테스트
 */
class SearchKeywordIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private var ownerId = 0L
    private var otherId = 0L
    private lateinit var ownerToken: String

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        ownerId = createActiveUser()
        otherId = createActiveUser()
        ownerToken = mintAccessToken(ownerId)
    }

    @Test
    fun `최근 검색어는 최신순, 추천 검색어는 sort_order 순으로 반환된다`() {
        val base = LocalDateTime.now()
        persistRecent(ownerId, "아이폰 케이스", base.minusMinutes(5))
        persistRecent(ownerId, "노트북", base.minusMinutes(1))
        persistRecent(ownerId, "키보드", base.minusMinutes(3))
        // 삽입 순서(자동급식기·사료·간식)와 sort_order(3·1·2)를 어긋나게 둔다.
        // 기대값 [사료·간식·자동급식기]는 sort_order asc로만 나오는 순서라 id asc/desc·삽입순 회귀를 모두 가른다.
        persistRecommended("자동급식기", sortOrder = 3, isActive = true)
        persistRecommended("사료", sortOrder = 1, isActive = true)
        persistRecommended("간식", sortOrder = 2, isActive = true)

        val body = getKeywords(ownerToken)

        assertEquals(
            listOf("노트북", "키보드", "아이폰 케이스"),
            JsonPath.read<List<String>>(body, "$.data.recentKeywords[*].keyword"),
            "최근 검색어는 searched_at 최신순이어야 한다",
        )
        assertEquals(
            listOf("사료", "간식", "자동급식기"),
            JsonPath.read<List<String>>(body, "$.data.recommendedKeywords[*].keyword"),
            "추천 검색어는 삽입 순서·id와 무관하게 sort_order 오름차순이어야 한다",
        )
    }

    @Test
    fun `최근 검색어는 입력 원본 표기와 검색 시각을 반환한다`() {
        persistRecent(ownerId, "The North Face", LocalDateTime.now())

        val body = getKeywords(ownerToken)

        assertEquals(
            "The North Face",
            JsonPath.read<String>(body, "$.data.recentKeywords[0].keyword"),
            "원본 표기가 보존돼야 한다",
        )
        assertTrue(
            JsonPath.read<Any?>(body, "$.data.recentKeywords[0].searchedAt") != null,
            "검색 시각(searchedAt)이 응답 계약에 포함돼야 한다",
        )
    }

    @Test
    fun `비활성 추천 검색어는 노출되지 않는다`() {
        persistRecommended("사료", sortOrder = 1, isActive = true)
        persistRecommended("품절키워드", sortOrder = 2, isActive = false)

        val body = getKeywords(ownerToken)

        assertEquals(
            listOf("사료"),
            JsonPath.read<List<String>>(body, "$.data.recommendedKeywords[*].keyword"),
            "is_active=false는 제외돼야 한다",
        )
    }

    @Test
    fun `본인 최근 검색어만 반환한다`() {
        persistRecent(ownerId, "내 검색", LocalDateTime.now())
        persistRecent(otherId, "남 검색", LocalDateTime.now())

        val body = getKeywords(ownerToken)

        assertEquals(
            listOf("내 검색"),
            JsonPath.read<List<String>>(body, "$.data.recentKeywords[*].keyword"),
            "타인의 최근 검색어는 보이지 않아야 한다",
        )
    }

    @Test
    fun `최근 검색어가 없어도 추천 검색어는 노출되고 최근은 빈 배열이다`() {
        persistRecommended("사료", sortOrder = 1, isActive = true)

        val body = getKeywords(ownerToken)

        assertEquals(0, JsonPath.read<List<*>>(body, "$.data.recentKeywords").size, "최근 검색어 없음은 빈 배열")
        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.recommendedKeywords").size, "추천은 그대로 노출")
    }

    @Test
    fun `최근 검색어는 최신 10개까지만 반환한다`() {
        val base = LocalDateTime.now()
        repeat(15) { i -> persistRecent(ownerId, "kw$i", base.minusMinutes((15 - i).toLong())) }

        val body = getKeywords(ownerToken)
        val keywords = JsonPath.read<List<String>>(body, "$.data.recentKeywords[*].keyword")

        assertEquals(10, keywords.size, "최근 검색어는 10개로 제한된다")
        assertEquals("kw14", keywords.first(), "가장 최근 검색어가 맨 앞이어야 한다")
        assertEquals("kw5", keywords.last(), "11번째부터는 잘려야 한다")
    }

    @Test
    fun `같은 사용자에 같은 키워드는 유일 제약으로 두 번 저장되지 않는다`() {
        persistRecent(ownerId, "노트북", LocalDateTime.now())

        assertThrows(Exception::class.java) {
            persistRecent(ownerId, "노트북", LocalDateTime.now())
        }

        // 두 번째 삽입이 막힌 뒤에도 첫 행이 한 행으로 살아있어야 한다(덮어쓰기·중복 없음).
        val body = getKeywords(ownerToken)
        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.recentKeywords").size, "유일 제약으로 한 행만 남는다")
        assertEquals("노트북", JsonPath.read<String>(body, "$.data.recentKeywords[0].keyword"), "첫 행이 보존된다")
    }

    @Test
    fun `서로 다른 사용자는 같은 키워드를 각자 가질 수 있다`() {
        persistRecent(ownerId, "노트북", LocalDateTime.now())
        persistRecent(otherId, "노트북", LocalDateTime.now())

        assertEquals(1, JsonPath.read<List<*>>(getKeywords(ownerToken), "$.data.recentKeywords").size)
    }

    @Test
    fun `대소문자가 다른 키워드는 서로 다른 최근 검색어로 저장된다`() {
        val base = LocalDateTime.now()
        persistRecent(ownerId, "iPhone", base)
        persistRecent(ownerId, "iphone", base.minusMinutes(1))

        val keywords = JsonPath.read<List<String>>(getKeywords(ownerToken), "$.data.recentKeywords[*].keyword")

        assertEquals(2, keywords.size, "as_cs 콜레이션이라 대소문자가 다르면 각각 저장된다")
        assertTrue(keywords.containsAll(listOf("iPhone", "iphone")), "친 대소문자가 각각 보존된다")
    }

    @Test
    fun `미인증 요청은 401을 반환한다`() {
        mockMvc.perform(get("/api/v1/search/keywords")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(get("/api/v1/search/keywords").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
            .andExpect(status().isUnauthorized)
    }

    // --- helpers ---

    private fun getKeywords(token: String): String =
        mockMvc
            .perform(get("/api/v1/search/keywords").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun persistRecent(
        userId: Long,
        keyword: String,
        searchedAt: LocalDateTime,
    ) {
        tx.execute {
            em.persist(RecentSearch.record(userId, keyword, searchedAt))
            em.flush()
        }
    }

    private fun persistRecommended(
        keyword: String,
        sortOrder: Int,
        isActive: Boolean,
    ) {
        tx.execute {
            em.persist(RecommendedKeyword.register(keyword, sortOrder, isActive))
            em.flush()
        }
    }
}
