package com.aechak.api.product.search

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.seller.seller.Seller
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 키워드 상품 검색의 FULLTEXT 술어(MATCH로 후보를 좁히고 LIKE로 재검사)가 LIKE와 같은 결과를 내는지,
 * LIKE로 되돌아가는 검색어와 남는 차이(불용어)가 무엇인지 고정하는 통합 테스트
 */
class ProductKeywordSearchFulltextIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        tx.execute {
            em.persist(Seller.open(userId = SELLER_ID, storeName = "store-$SELLER_ID", baseShippingFee = 3000L))
            val mid = Category.create(Category.create(null, 1, "강아지", null, 1).also { em.persist(it) }, 2, "사료간식", null, 1)
            em.persist(mid)
            FIXTURE_NAMES.forEach { persistProduct(mid, it) }
        }
    }

    @Test
    fun `테스트 스키마의 products에 ngram FULLTEXT 인덱스가 있다`() {
        val indexes =
            jdbc.queryForList(
                "SELECT CONCAT(INDEX_NAME, ':', INDEX_TYPE, ':', COLUMN_NAME) FROM information_schema.STATISTICS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND INDEX_TYPE = 'FULLTEXT'",
                String::class.java,
            )
        assertEquals(listOf("ft_products_name:FULLTEXT:name"), indexes, "공용 테스트 설정이 ddl-auto 뒤에 인덱스를 추가해야 한다")
    }

    @Test
    fun `2글자 이상 검색어는 목록과 총개수 SQL 모두 MATCH AGAINST와 LIKE를 함께 건다`() {
        val sql = captureSql { search("강아지") }

        assertEquals(2, sql.size, "첫 페이지는 목록과 총개수 두 쿼리: $sql")
        sql.forEach {
            assertTrue(MATCH_AGAINST.containsMatchIn(it), "MATCH AGAINST 불리언 모드로 렌더링돼야 한다: $it")
            assertTrue(it.contains(" like "), "LIKE 재검사가 함께 걸려야 한다: $it")
        }
    }

    @Test
    fun `2글자 미만 검색어의 SQL은 MATCH 없이 LIKE만 건다`() {
        val sql = captureSql { search("개") }

        assertEquals(2, sql.size, "첫 페이지는 목록과 총개수 두 쿼리: $sql")
        sql.forEach {
            assertTrue(!MATCH_AGAINST.containsMatchIn(it) && it.contains(" like "), "LIKE만 걸려야 한다: $it")
        }
    }

    @Test
    fun `fixture의 검색어마다 결과와 총개수가 LIKE만 쓴 결과와 같다`() {
        listOf("강아지", "사료", "강아지 사료", "아지 사료", "사료 강아", "고양이", "대형견", "dog bed").forEach { keyword ->
            val body = search(keyword)
            val expected = likeOnly(keyword)
            assertTrue(expected.isNotEmpty(), "$keyword: 비교할 LIKE 결과가 있어야 한다")
            assertEquals(expected, productNames(body).sorted(), "$keyword 목록")
            assertEquals(expected.size, totalCount(body), "$keyword 총개수")
        }
    }

    @Test
    fun `상품명에 띄어 쓴 ngram이 있으면 MATCH에는 걸리지만 LIKE 재검사가 거른다`() {
        assertTrue("강아 아지 간식" in matchOnly("\"강아지\""), "phrase가 ngram으로 바뀌어 띄어 쓴 강아 아지에도 일치한다")

        val names = productNames(search("강아지"))

        assertTrue("강아 아지 간식" !in names, "LIKE와 같게 연속한 강아지만 남는다: $names")
        assertEquals(likeOnly("강아지"), names.sorted())
    }

    @Test
    fun `공백이 든 검색어는 공백까지 연속 일치하는 상품만 남는다`() {
        assertTrue(
            matchOnly("\"강아지 사료\"").containsAll(listOf("강아지  사료", "강아지-사료 세트")),
            "구문 검색은 구분 문자의 종류와 개수를 보지 않는다",
        )

        val names = productNames(search("강아지 사료"))

        assertEquals(listOf("강아지 사료", "프리미엄 강아지 사료 2kg"), names.sorted())
    }

    @Test
    fun `2글자 미만 조각이 있는 검색어는 LIKE로 되돌아가 MATCH가 놓치는 상품도 찾는다`() {
        mapOf("개" to listOf("강아지 개 목줄", "개껌"), "사료 강" to listOf("사료 강아지 세트")).forEach { (keyword, expected) ->
            assertEquals(emptyList<String>(), matchOnly("\"$keyword\""), "$keyword: ngram 토큰 2글자보다 짧은 조각은 색인 토큰이 없다")
            assertEquals(expected, productNames(search(keyword)).sorted(), keyword)
        }
    }

    @Test
    fun `ASCII 밖 구분 문자가 든 검색어는 LIKE로 되돌아간다`() {
        assertEquals(emptyList<String>(), matchOnly("\"강아지·고양이\""), "가운데점 앞뒤 조각의 구문 일치를 놓친다")

        assertEquals(listOf("강아지·고양이 겸용"), productNames(search("강아지·고양이")))
    }

    @Test
    fun `불리언 연산자 문자는 연산자가 아니라 글자로 일치한다`() {
        mapOf(
            "+강아지" to listOf("+강아지 특가"),
            "-사료" to listOf("강아지-사료 세트"),
            "(대형견)" to listOf("강아지(대형견) 사료"),
            "강아지*" to emptyList(),
            "<강아지>" to emptyList(),
            "~강아지" to emptyList(),
            "@강아지" to emptyList(),
            "\"강아지" to emptyList(),
            "+-" to emptyList(),
        ).forEach { (keyword, expected) ->
            val body = search(keyword)
            assertEquals(expected, productNames(body).sorted(), keyword)
            assertEquals(expected.size, totalCount(body), "$keyword 총개수")
        }
    }

    @Test
    fun `라틴 글자가 든 검색어는 MATCH가 불용어 토큰을 놓치므로 LIKE로 되돌아간다`() {
        // innodb_ft_enable_stopword 기본 ON에서 a, at 같은 불용어가 든 토큰은 색인에서 빠짐
        assertEquals(emptyList<String>(), matchOnly("\"cat\""), "cat의 토큰 ca, at가 모두 불용어를 포함한다")

        captureSql { search("cat") }.forEach {
            assertTrue(!MATCH_AGAINST.containsMatchIn(it) && it.contains(" like "), "LIKE만 걸려야 한다: $it")
        }
        assertEquals(listOf("cat toy"), productNames(search("cat")))
        assertEquals(listOf("dog bed"), productNames(search("dog")))
    }

    // --- helpers ---

    private fun search(keyword: String): String =
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", keyword).param("size", "100"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun productNames(body: String): List<String> = JsonPath.read(body, "$.data.products[*].name")

    private fun totalCount(body: String): Int = JsonPath.read(body, "$.data.totalCount")

    private fun likeOnly(keyword: String): List<String> =
        jdbc.queryForList("SELECT name FROM products WHERE name LIKE CONCAT('%', ?, '%')", String::class.java, keyword).sorted()

    private fun matchOnly(query: String): List<String> =
        jdbc.queryForList(
            "SELECT name FROM products WHERE MATCH(name) AGAINST(? IN BOOLEAN MODE)",
            String::class.java,
            query,
        )

    /** org.hibernate.SQL 로그를 잠깐 켜서 블록이 products에 실행한 select 문을 모은다 */
    private fun captureSql(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger("org.hibernate.SQL") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previous = logger.level
        logger.level = Level.DEBUG
        logger.addAppender(appender)
        try {
            block()
            return appender.list
                .map { it.formattedMessage.replace(Regex("\\s+"), " ").trim() }
                .filter { it.startsWith("select") && it.contains(" from products ") }
        } finally {
            logger.detachAppender(appender)
            logger.level = previous
        }
    }

    private fun persistProduct(
        category: Category,
        name: String,
    ) = em.persist(
        Product.register(
            category = category,
            sellerId = SELLER_ID,
            name = name,
            description = null,
            representativeImageKey = "products/${name.hashCode()}.jpg",
            regularPrice = 10000L,
            discountPrice = null,
            discountStartAt = null,
            discountEndAt = null,
        ),
    )

    companion object {
        private const val SELLER_ID = 77L
        private val MATCH_AGAINST = Regex("match\\(\\w+\\.name\\) against \\(\\? in boolean mode\\)")
        private val FIXTURE_NAMES =
            listOf(
                "강아지 사료",
                "프리미엄 강아지 사료 2kg",
                "강아지사료",
                "강아지  사료",
                "사료 강아지 세트",
                "강아 아지 간식",
                "고양이 사료",
                "개껌",
                "강아지 개 목줄",
                "+강아지 특가",
                "강아지(대형견) 사료",
                "강아지-사료 세트",
                "강아지·고양이 겸용",
                "cat toy",
                "dog bed",
            )
    }
}
