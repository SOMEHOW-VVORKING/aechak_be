package com.aechak.api.product.search

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.application.product.search.service.ProductKeywordSearchService
import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.category.repository.CategoryRepository
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.infra.persistence.product.search.ProductKeywordSearchQueryAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 키워드 검색의 세 경로가 같은 결과를 내는지, 표본 일치 비율로 경로를 고르는지 고정하는 통합 테스트
 * 인덱스 순서 경로는 정렬 인덱스로 읽다 멈추고, FULLTEXT 경로는 MATCH로 후보를 좁히고, 스캔 경로는 테이블 전체를 읽어 정렬한다
 */
class ProductKeywordSearchAccessPathIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Test
    fun `세 경로가 정렬, 필터, 커서 순회 전체에서 같은 결과를 낸다`() {
        val mid = persistFixture()
        // FULLTEXT 기준 0이면 MATCH를 안 쓰고, 인덱스 기준 0이면 늘 인덱스 순서로, 2면 늘 스캔으로 읽는다
        val indexWalk = service(fulltextMaxMatchRatio = 0.0, indexWalkMinMatchRatio = 0.0)
        val fulltext = service(fulltextMaxMatchRatio = 2.0, indexWalkMinMatchRatio = 2.0)
        val scan = service(fulltextMaxMatchRatio = 0.0, indexWalkMinMatchRatio = 2.0)
        val filters: List<(ProductKeywordSearchQuery) -> ProductKeywordSearchQuery> =
            listOf(
                { it },
                { it.copy(minPrice = 10000L, maxPrice = 15000L) },
                { it.copy(freeShipping = true) },
                { it.copy(excludeSoldOut = true) },
                { it.copy(minRating = BigDecimal("3.5")) },
                { it.copy(categoryId = mid.id) },
            )

        // 사는 1글자라 MATCH를 쓸 수 없어 fulltext 서비스에서도 스캔 경로로 읽는다
        for (keyword in listOf("강아지", "사료", "간식", "강아지 사료", "사", "없는상품")) {
            for (sort in ProductKeywordSearchSort.entries) {
                filters.forEachIndexed { i, filter ->
                    val query = filter(ProductKeywordSearchQuery(keyword = keyword, sort = sort, size = 3))
                    val expected = traverse(fulltext, query)
                    assertEquals(expected, traverse(indexWalk, query), "인덱스 순서 경로 $keyword $sort 필터 $i")
                    assertEquals(expected, traverse(scan, query), "스캔 경로 $keyword $sort 필터 $i")
                    if (keyword != "없는상품" && i == 0) assertTrue(expected.size > 1, "$keyword $sort: 커서로 여러 페이지를 넘겨야 한다")
                }
            }
        }
    }

    @Test
    fun `인덱스 순서 경로의 낮은 가격순은 할인 중인 상품과 아닌 상품을 유효가격과 id 순서로 합친다`() {
        tx.execute {
            val mid = persistMidCategory("가격")
            persistSeller(SELLER, baseShippingFee = 3000L)
            persistProduct(mid, "가격 사료 정가", regular = 10000L)
            persistProduct(mid, "가격 사료 할인중", regular = 15000L, discount = 10000L, start = NOW.minusDays(1), end = NOW.plusDays(1))
            persistProduct(mid, "가격 사료 할인끝", regular = 12000L, discount = 5000L, start = NOW.minusDays(9), end = NOW.minusDays(1))
            persistProduct(mid, "가격 사료 할인전", regular = 9000L, discount = 1000L, start = NOW.plusDays(1), end = NOW.plusDays(9))
            persistProduct(mid, "가격 사료 종료없음", regular = 20000L, discount = 8000L, start = NOW.minusDays(1))
        }
        val expected = listOf("가격 사료 종료없음", "가격 사료 할인전", "가격 사료 할인중", "가격 사료 정가", "가격 사료 할인끝")
        val query = ProductKeywordSearchQuery(keyword = "사료", sort = ProductKeywordSearchSort.PRICE_ASC, size = 2)

        val names = traverse(service(fulltextMaxMatchRatio = 0.0, indexWalkMinMatchRatio = 0.0), query).flatMap { it.names }

        assertEquals(expected, names)
    }

    @Test
    fun `인덱스 순서 경로의 낮은 가격순은 한쪽에만 일치 상품이 몰려도 같은 결과를 낸다`() {
        tx.execute {
            val mid = persistMidCategory("가격")
            persistSeller(SELLER, baseShippingFee = 3000L)
            (1..5).forEach { persistProduct(mid, "할인 연어 $it", regular = 20000L + it, discount = 9000L + it, start = NOW.minusDays(1)) }
            (1..5).forEach { persistProduct(mid, "정가 간식 $it", regular = 8000L + it) }
        }

        listOf("연어" to "할인 연어", "간식" to "정가 간식").forEach { (keyword, prefix) ->
            val names =
                traverse(
                    service(fulltextMaxMatchRatio = 0.0, indexWalkMinMatchRatio = 0.0),
                    ProductKeywordSearchQuery(keyword = keyword, sort = ProductKeywordSearchSort.PRICE_ASC, size = 2),
                ).flatMap { it.names }
            assertEquals((1..5).map { "$prefix $it" }, names, keyword)
        }
    }

    @Test
    fun `표본 일치 비율로 FULLTEXT, 인덱스 순서, 스캔 경로를 고른다`() {
        persistFixture()
        val service = service(fulltextMaxMatchRatio = 0.05, indexWalkMinMatchRatio = 0.01)

        val rare = captureSql { service.searchPage(ProductKeywordSearchQuery(keyword = "한정판"), NOW) }.filterNot { it.contains("sum(case") }
        val common =
            captureSql {
                service.searchPage(
                    ProductKeywordSearchQuery(keyword = "강아지"),
                    NOW,
                )
            }.filterNot { it.contains("sum(case") }
        val none = captureSql { service.searchPage(ProductKeywordSearchQuery(keyword = "%"), NOW) }.filterNot { it.contains("sum(case") }

        assertTrue(
            rare.single().let { MATCH_AGAINST.containsMatchIn(it) && it.contains(" over()") },
            "표본 36개 중 1개만 일치하면 FULLTEXT 경로: $rare",
        )
        assertEquals(2, common.size, "표본 절반 넘게 일치하면 인덱스 순서로 읽고 총개수를 따로 센다: $common")
        assertTrue(common.none { MATCH_AGAINST.containsMatchIn(it) || it.contains(" over()") }, common.toString())
        assertTrue(none.single().let { !MATCH_AGAINST.containsMatchIn(it) && it.contains(" over()") }, "MATCH를 못 쓰고 드문 검색어는 스캔 경로: $none")
        assertTrue(none.single().contains("review_count+"), "스캔 경로는 정렬 인덱스를 고르지 못하게 정렬 열에 0을 더한다: $none")
    }

    @Test
    fun `인덱스 순서 경로의 총개수 쿼리는 셀러를 조인하지 않고 목록은 셀러를 왼쪽 조인한다`() {
        persistFixture()

        val sql =
            captureSql {
                service(
                    fulltextMaxMatchRatio = 0.0,
                    indexWalkMinMatchRatio = 0.0,
                ).searchPage(ProductKeywordSearchQuery(keyword = "강아지"), NOW)
            }

        val count = sql.single { it.startsWith("select count(") && !it.contains("sum(case") }
        assertFalse(count.contains(" join "), "총개수 쿼리는 상품만 읽는다: $count")
        val list = sql.single { it.contains(" order by ") }
        assertTrue(list.contains(" left join sellers "), "목록은 셀러 상호만 왼쪽 조인으로 붙인다: $list")
        assertFalse(Regex(" (inner )?join sellers ").containsMatchIn(list.replace(" left join sellers ", " ")), list)
    }

    // --- helpers ---

    /** 경로 기준 비율만 다른 어댑터로 서비스를 만듦. QueryDSL은 영속성 모듈에 가둬서 클래스 이름으로 빈을 꺼냄 */
    private fun service(
        fulltextMaxMatchRatio: Double,
        indexWalkMinMatchRatio: Double,
    ): ProductKeywordSearchService {
        val queryFactory = context.getBean(Class.forName("com.querydsl.jpa.impl.JPAQueryFactory"))
        val adapter =
            ProductKeywordSearchQueryAdapter::class
                .constructors
                .single()
                .call(queryFactory, true, 3000, fulltextMaxMatchRatio, indexWalkMinMatchRatio)
        return ProductKeywordSearchService(adapter, categoryRepository)
    }

    /** 첫 페이지부터 커서를 따라 마지막 페이지까지 읽음 */
    private fun traverse(
        service: ProductKeywordSearchService,
        query: ProductKeywordSearchQuery,
    ): List<Page> {
        val pages = mutableListOf<Page>()
        var cursor: String? = null
        do {
            val result: CursorPageResult<ProductCatalogView> = tx.execute { service.searchPage(query.copy(cursor = cursor), NOW) }!!
            pages += Page(result.items.map { it.name }, result.totalCount, result.hasNext)
            cursor = result.nextCursor
        } while (cursor != null && pages.size < MAX_PAGES)
        return pages
    }

    private data class Page(
        val names: List<String>,
        val totalCount: Long?,
        val hasNext: Boolean,
    )

    /**
     * 셀러 셋(유료 배송, 무료 배송, 정지), 활성 중분류 둘과 비활성 중분류 하나에 상품 36개
     * 가격, 리뷰 수, 할인 기간(진행, 종료, 시작 전, 종료일 없음), 판매 상태, 검수 상태를 섞고 같은 정렬 키를 일부러 만든다
     */
    private fun persistFixture(): Category =
        tx.execute {
            persistSeller(SELLER, baseShippingFee = 3000L)
            persistSeller(FREE_SELLER, baseShippingFee = 0L)
            persistSeller(SUSPENDED_SELLER, baseShippingFee = 0L)
            val mid = persistMidCategory("사료간식")
            val otherMid = persistMidCategory("용품")
            val inactiveMid = persistMidCategory("단종")
            val products =
                (0 until 36).map { i ->
                    val name =
                        listOf(
                            if (i % 5 < 3) "강아지" else "고양이",
                            when (i % 5) {
                                0, 1 -> "사료"
                                2 -> "간식"
                                else -> "장난감"
                            },
                            if (i == 17) "한정판" else null,
                            "$i",
                        ).filterNotNull().joinToString(" ")
                    val regular = 10000L + (i % 4) * 5000L
                    val discount = if (i % 6 < 4) regular - 5000L else null
                    val (start, end) =
                        when (i % 6) {
                            0 -> NOW.minusDays(1) to NOW.plusDays(1)
                            1 -> NOW.minusDays(9) to NOW.minusDays(1)
                            2 -> NOW.plusDays(1) to NOW.plusDays(9)
                            3 -> NOW.minusDays(2) to null
                            else -> null to null
                        }
                    val sellerId =
                        when {
                            i % 9 == 8 -> SUSPENDED_SELLER
                            i % 3 == 0 -> FREE_SELLER
                            else -> SELLER
                        }
                    val category =
                        when {
                            i % 12 == 11 -> inactiveMid
                            i % 2 == 0 -> mid
                            else -> otherMid
                        }
                    persistProduct(category, name, regular, discount, start, end, sellerId)
                }
            em.flush()
            products.forEachIndexed { i, p ->
                if (i % 7 == 6) setSaleStatus(p.id, SaleStatus.OUT_OF_STOCK)
                if (i % 11 == 10) setSaleStatus(p.id, SaleStatus.SUSPENDED)
                if (i % 13 == 12) setInspectionStatus(p.id, InspectionStatus.PENDING)
                if (i % 4 !=
                    3
                ) {
                    persistStats(
                        p.id,
                        reviewCount = (i * 7) % 5,
                        averageRating =
                            BigDecimal("3.00").add(
                                BigDecimal("0.5").multiply(
                                    BigDecimal(i % 3),
                                ),
                            ),
                    )
                }
            }
            em
                .createQuery("update Seller s set s.status = :status where s.userId = :id")
                .setParameter("status", SellerStatus.SUSPENDED)
                .setParameter("id", SUSPENDED_SELLER)
                .executeUpdate()
            em
                .createQuery("update Category c set c.status = :status where c.id = :id")
                .setParameter("status", CategoryStatus.INACTIVE)
                .setParameter("id", inactiveMid.id)
                .executeUpdate()
            mid
        }!!

    private fun persistSeller(
        sellerId: Long,
        baseShippingFee: Long,
    ) = em.persist(Seller.open(userId = sellerId, storeName = "store-$sellerId", baseShippingFee = baseShippingFee))

    private fun persistMidCategory(name: String): Category {
        val root = Category.create(null, 1, "$name 대분류", null, 1)
        em.persist(root)
        val mid = Category.create(root, 2, name, null, 1)
        em.persist(mid)
        return mid
    }

    private fun persistProduct(
        category: Category,
        name: String,
        regular: Long,
        discount: Long? = null,
        start: LocalDateTime? = null,
        end: LocalDateTime? = null,
        sellerId: Long = SELLER,
    ): Product =
        Product
            .register(
                category = category,
                sellerId = sellerId,
                name = name,
                description = null,
                representativeImageKey = "products/$name.jpg",
                regularPrice = regular,
                discountPrice = discount,
                discountStartAt = start,
                discountEndAt = end,
            ).also { em.persist(it) }

    private fun setSaleStatus(
        productId: Long,
        status: SaleStatus,
    ) = em
        .createQuery("update Product p set p.saleStatus = :status where p.id = :id")
        .setParameter("status", status)
        .setParameter("id", productId)
        .executeUpdate()

    private fun setInspectionStatus(
        productId: Long,
        status: InspectionStatus,
    ) = em
        .createQuery("update Product p set p.inspectionStatus = :status where p.id = :id")
        .setParameter("status", status)
        .setParameter("id", productId)
        .executeUpdate()

    /** 통계 행과 인기순이 읽는 products.review_count를 같은 값으로 둠 */
    private fun persistStats(
        productId: Long,
        reviewCount: Int,
        averageRating: BigDecimal,
    ) {
        em.persist(ProductStats.create(productId))
        em.flush()
        em
            .createQuery("update ProductStats s set s.reviewCount = :reviewCount, s.averageRating = :averageRating where s.productId = :id")
            .setParameter("reviewCount", reviewCount)
            .setParameter("averageRating", averageRating)
            .setParameter("id", productId)
            .executeUpdate()
        em
            .createNativeQuery("update products set review_count = :reviewCount where id = :id")
            .setParameter("reviewCount", reviewCount)
            .setParameter("id", productId)
            .executeUpdate()
    }

    /** org.hibernate.SQL 로그를 잠깐 켜서 블록이 실행한 select 문을 모음 */
    private fun captureSql(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger("org.hibernate.SQL") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previous = logger.level
        logger.level = Level.DEBUG
        logger.addAppender(appender)
        try {
            tx.execute { block() }
            return appender.list
                .map { it.formattedMessage.replace(Regex("\\s+"), " ").trim() }
                .filter { it.startsWith("select") && it.contains(" from products ") }
        } finally {
            logger.detachAppender(appender)
            logger.level = previous
        }
    }

    companion object {
        private const val SELLER = 201L
        private const val FREE_SELLER = 202L
        private const val SUSPENDED_SELLER = 203L
        private const val MAX_PAGES = 30
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 11, 5, 12, 0)
        private val MATCH_AGAINST = Regex("match\\(\\w+\\.name\\) against \\(\\? in boolean mode\\)")
    }
}
