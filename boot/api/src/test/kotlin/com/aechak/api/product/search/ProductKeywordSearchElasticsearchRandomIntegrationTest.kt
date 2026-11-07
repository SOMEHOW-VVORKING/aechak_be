package com.aechak.api.product.search

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.application.product.search.service.ProductKeywordSearchService
import com.aechak.application.product.search.service.ProductSearchReindexService
import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.category.repository.CategoryRepository
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.infra.elasticsearch.product.search.ElasticsearchProductKeywordSearchAdapter
import com.aechak.infra.persistence.product.search.ProductKeywordSearchQueryAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 시드를 고정한 무작위 상품과 무작위 검색 조건에서 Elasticsearch 어댑터가 JPA 어댑터와 같은 결과를 내는지 비교하는 회귀 테스트
 * 고정 픽스처 테스트가 다루지 않은 조합을 찾는다. 실패 메시지의 시드와 검색 조건으로 같은 입력을 다시 만든다
 */
class ProductKeywordSearchElasticsearchRandomIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Autowired
    private lateinit var jpaAdapter: ProductKeywordSearchQueryAdapter

    @Autowired
    private lateinit var esAdapter: ElasticsearchProductKeywordSearchAdapter

    @Autowired
    private lateinit var reindexService: ProductSearchReindexService

    @Test
    fun `무작위 상품과 무작위 검색 조건에서 Elasticsearch 어댑터가 모든 페이지를 JPA 어댑터와 같게 낸다`() {
        val random = Random(SEED)
        val fixture = persistFixture(random)
        assertEquals(PRODUCT_COUNT.toLong(), reindexService.reindexAll(), "seed=$SEED 모든 상품을 색인한다")
        val jpa = ProductKeywordSearchService(jpaAdapter, categoryRepository)
        val es = ProductKeywordSearchService(esAdapter, categoryRepository)

        var nonEmpty = 0
        repeat(QUERY_COUNT) { n ->
            val query = randomQuery(random, fixture)
            val expected = traverse(jpa, query)
            assertEquals(expected, traverse(es, query), "seed=$SEED query#$n $query")
            if (expected.first().items.isNotEmpty()) nonEmpty++
        }
        assertTrue(nonEmpty > QUERY_COUNT / 3, "seed=$SEED 결과가 있는 검색이 $nonEmpty/$QUERY_COUNT 개뿐이라 비교가 약하다")
    }

    // --- 무작위 검색 조건 ---

    private fun randomQuery(
        random: Random,
        fixture: Fixture,
    ): ProductKeywordSearchQuery {
        var keyword: String
        do keyword = randomKeyword(random, fixture.names) while (keyword.isBlank())
        val prices = listOf(random.nextPrice(), random.nextPrice()).sorted()
        return ProductKeywordSearchQuery(
            keyword = keyword,
            sort = ProductKeywordSearchSort.entries.random(random),
            minPrice = prices[0].takeIf { random.nextInt(4) == 0 },
            maxPrice = prices[1].takeIf { random.nextInt(4) == 0 },
            minRating = randomMinRating(random, fixture.ratings).takeIf { random.nextInt(4) == 0 },
            categoryId = fixture.filterCategoryIds.random(random).takeIf { random.nextInt(4) == 0 },
            freeShipping = random.nextInt(4) == 0,
            excludeSoldOut = random.nextInt(4) == 0,
            size = random.nextInt(1, 6),
        )
    }

    /** 상품명에서 1에서 6글자를 잘라낸 검색어를 주로 쓰고, 단어, 두 단어, 특수문자, 없는 검색어를 섞음 */
    private fun randomKeyword(
        random: Random,
        names: List<String>,
    ): String =
        when (random.nextInt(10)) {
            in 0..4 -> {
                val name = names.random(random)
                val length = random.nextInt(1, 7).coerceAtMost(name.length)
                val start = random.nextInt(name.length - length + 1)
                name.substring(start, start + length).let { if (random.nextInt(5) == 0) it.uppercase() else it }
            }

            5, 6 -> {
                TOKENS.random(random)
            }

            7 -> {
                "${TOKENS.random(random)} ${TOKENS.random(random)}"
            }

            8 -> {
                EDGE_KEYWORDS.random(random)
            }

            else -> {
                "없는상품${random.nextInt(100)}"
            }
        }

    /** 상품 별점과 같은 값으로 경계를 만들거나 0.1, 0.01 단위 값을 고름 */
    private fun randomMinRating(
        random: Random,
        ratings: List<BigDecimal>,
    ): BigDecimal =
        when (random.nextInt(3)) {
            0 -> ratings.random(random)
            1 -> BigDecimal(random.nextInt(51)).movePointLeft(1)
            else -> BigDecimal(random.nextInt(501)).movePointLeft(2)
        }

    // --- 무작위 상품 ---

    private class Fixture(
        val names: List<String>,
        val ratings: List<BigDecimal>,
        val filterCategoryIds: List<Long>,
    )

    /**
     * 셀러 넷(유료 배송, 무료 배송, 정지, 행 없음), 중분류 셋(하나 비활성)과 소분류 셋(활성 중분류 아래 활성과 비활성, 비활성 중분류 아래 활성)에 상품을 만듦
     * 가격, 할인 기간, 리뷰 수, 별점은 작은 후보 집합에서 골라 정렬 키 동률과 필터 경계를 일부러 만듦
     */
    private fun persistFixture(random: Random): Fixture =
        tx.execute {
            persistSeller(SELLER, baseShippingFee = 3000L)
            persistSeller(FREE_SELLER, baseShippingFee = 0L)
            persistSeller(SUSPENDED_SELLER, baseShippingFee = 0L)
            val midA = persistCategory(persistCategory(null, 1, "강아지"), 2, "사료간식")
            val midB = persistCategory(persistCategory(null, 1, "고양이"), 2, "용품")
            val inactiveMid = persistCategory(persistCategory(null, 1, "단종"), 2, "단종 중분류")
            val leafA = persistCategory(midA, 3, "건식")
            val inactiveLeafA = persistCategory(midA, 3, "습식")
            val leafUnderInactive = persistCategory(inactiveMid, 3, "단종 소분류")
            val categories = listOf(midA, midA, midB, midB, leafA, inactiveMid, inactiveLeafA, leafUnderInactive)
            val sellers = listOf(SELLER, SELLER, FREE_SELLER, FREE_SELLER, SUSPENDED_SELLER, MISSING_SELLER)

            val products =
                (0 until PRODUCT_COUNT).map {
                    val regular = random.nextPrice()
                    val offset = DISCOUNT_OFFSETS.random(random)
                    val discount = (regular - offset).coerceAtLeast(0L).takeIf { random.nextInt(5) < 3 }
                    val (start, end) = if (discount == null) null to null else DISCOUNT_WINDOWS.random(random)
                    persistProduct(categories.random(random), randomName(random), regular, discount, start, end, sellers.random(random))
                }
            em.flush()

            products.groupBy { SALE_STATUSES.random(random) }.forEach { (status, ps) -> setProductColumn(ps, "saleStatus", status) }
            products.groupBy { INSPECTION_STATUSES.random(random) }.forEach { (s, ps) -> setProductColumn(ps, "inspectionStatus", s) }
            val ratings =
                products.mapNotNull { p ->
                    if (random.nextInt(4) == 0) return@mapNotNull null
                    val rating = if (random.nextInt(6) == 0) null else randomRating(random)
                    persistStats(p.id, reviewCount = random.nextInt(5), averageRating = rating)
                    rating
                }
            products.groupBy { random.nextInt(5) }.forEach { (count, ps) -> setProductReviewCount(ps, count) }
            em
                .createQuery("update Seller s set s.status = :status where s.userId = :id")
                .setParameter("status", SellerStatus.SUSPENDED)
                .setParameter("id", SUSPENDED_SELLER)
                .executeUpdate()
            em
                .createQuery("update Category c set c.status = :status where c.id in :ids")
                .setParameter("status", CategoryStatus.INACTIVE)
                .setParameter("ids", listOf(inactiveMid.id, inactiveLeafA.id))
                .executeUpdate()
            Fixture(products.map { it.name }, ratings, listOf(midA.id, midB.id))
        }!!

    /** 단어 두셋에서 다섯을 공백, 겹공백, 붙여 쓰기, 특수문자로 잇고 라틴 글자 대소문자를 섞음 */
    private fun randomName(random: Random): String =
        (1..random.nextInt(2, 6))
            .map {
                val token = (if (random.nextInt(4) == 0) EDGE_TOKENS else TOKENS).random(random)
                when (random.nextInt(3)) {
                    0 -> token.uppercase()
                    1 -> token.lowercase()
                    else -> token
                }
            }.reduce { acc, token -> acc + SEPARATORS.random(random) + token }

    private fun randomRating(random: Random): BigDecimal =
        if (random.nextBoolean()) {
            BigDecimal(random.nextInt(51)).movePointLeft(1).setScale(2)
        } else {
            BigDecimal(random.nextInt(21) * 25).movePointLeft(2)
        }

    private fun Random.nextPrice(): Long = PRICES.random(this)

    // --- 순회와 저장 도우미 ---

    /** 첫 페이지부터 커서를 따라 마지막 페이지까지 읽음 */
    private fun traverse(
        service: ProductKeywordSearchService,
        query: ProductKeywordSearchQuery,
    ): List<Page> {
        val pages = mutableListOf<Page>()
        var cursor: String? = null
        do {
            val result = tx.execute { service.searchPage(query.copy(cursor = cursor), NOW) }!!
            pages += Page(result.items, result.totalCount, result.hasNext)
            cursor = result.nextCursor
        } while (cursor != null && pages.size < MAX_PAGES)
        return pages
    }

    private data class Page(
        val items: List<ProductCatalogView>,
        val totalCount: Long?,
        val hasNext: Boolean,
    )

    private fun persistSeller(
        sellerId: Long,
        baseShippingFee: Long,
    ) = em.persist(Seller.open(userId = sellerId, storeName = "store-$sellerId", baseShippingFee = baseShippingFee))

    private fun persistCategory(
        parent: Category?,
        depth: Int,
        name: String,
    ): Category = Category.create(parent, depth, name, null, 1).also { em.persist(it) }

    private fun persistProduct(
        category: Category,
        name: String,
        regular: Long,
        discount: Long?,
        start: LocalDateTime?,
        end: LocalDateTime?,
        sellerId: Long,
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

    private fun setProductColumn(
        products: List<Product>,
        column: String,
        value: Any,
    ) = em
        .createQuery("update Product p set p.$column = :value where p.id in :ids")
        .setParameter("value", value)
        .setParameter("ids", products.map { it.id })
        .executeUpdate()

    private fun persistStats(
        productId: Long,
        reviewCount: Int,
        averageRating: BigDecimal?,
    ) {
        em.persist(ProductStats.create(productId))
        em.flush()
        em
            .createQuery("update ProductStats s set s.reviewCount = :reviewCount, s.averageRating = :averageRating where s.productId = :id")
            .setParameter("reviewCount", reviewCount)
            .setParameter("averageRating", averageRating)
            .setParameter("id", productId)
            .executeUpdate()
    }

    /** 인기순이 읽는 products.review_count를 통계 행과 따로 정해 두 열을 섞어 읽으면 결과가 달라지게 함 */
    private fun setProductReviewCount(
        products: List<Product>,
        reviewCount: Int,
    ) = em
        .createNativeQuery("update products set review_count = :reviewCount where id in (:ids)")
        .setParameter("reviewCount", reviewCount)
        .setParameter("ids", products.map { it.id })
        .executeUpdate()

    companion object {
        private const val SEED = 20261106L
        private const val PRODUCT_COUNT = 300
        private const val QUERY_COUNT = 200
        private const val MAX_PAGES = 400
        private const val SELLER = 401L
        private const val FREE_SELLER = 402L
        private const val SUSPENDED_SELLER = 403L
        private const val MISSING_SELLER = 404L

        // 요청 시각만 초나 분 단위로 자르는 실수도 드러나도록 밀리초까지 둠
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 11, 5, 12, 34, 56, 789_000_000)

        // MySQL 콜레이션(utf8mb4_0900_ai_ci)은 악센트와 전각, 반각을 무시하고 ES 소문자화는 무시하지 않는 알려진 차이라 이름과 검색어에 악센트 글자와 전각 문자를 넣지 않음
        private val TOKENS =
            listOf(
                "강아지",
                "고양이",
                "사료",
                "간식",
                "장난감",
                "연어",
                "고구마",
                "노령견",
                "트릿",
                "츄르",
                "유기농",
                "국내산",
                "하네스",
                "방석",
                "냥냥냥",
                "Dog",
                "CAT",
                "Premium",
                "toy",
                "Salmon",
                "mini",
                "aaaa",
            )
        private val EDGE_TOKENS =
            listOf("100%", "50%", "a_b", "_", "\"연어\"", "'츄르'", "+강아지", "-사료", "C++", "1+1", "x-large", "!", "\\", "(대)", "1kg")
        private val SEPARATORS = listOf(" ", " ", " ", "  ", "", "_", "-", "/", "\u00B7")
        private val EDGE_KEYWORDS = listOf("%", "_", "!", "\\", "\"", "'", "+", "-", "%_", "!%", "a_b", "+강아지 -사료", "\"연어\"")

        private val PRICES = listOf(100L, 1000L, 5000L, 9900L, 10000L, 15000L, 20000L)
        private val DISCOUNT_OFFSETS = listOf(0L, 100L, 900L, 5000L, 10000L, 20000L)

        // 진행, 종료일 없음, 종료, 시작 전, 요청 시각에 시작, 요청 시각에 종료, 1마이크로초 뒤 시작, 1마이크로초 전 종료, 시작과 종료가 요청 시각
        private val DISCOUNT_WINDOWS: List<Pair<LocalDateTime, LocalDateTime?>> =
            listOf(
                NOW.minusDays(1) to NOW.plusDays(1),
                NOW.minusDays(2) to null,
                NOW.minusDays(9) to NOW.minusDays(1),
                NOW.plusDays(1) to NOW.plusDays(9),
                NOW.plusDays(1) to null,
                NOW to NOW.plusDays(1),
                NOW.minusDays(1) to NOW,
                NOW.plusNanos(1_000) to null,
                NOW.minusDays(1) to NOW.minusNanos(1_000),
                NOW to NOW,
            )
        private val SALE_STATUSES =
            listOf(
                SaleStatus.ON_SALE,
                SaleStatus.ON_SALE,
                SaleStatus.ON_SALE,
                SaleStatus.ON_SALE,
                SaleStatus.OUT_OF_STOCK,
                SaleStatus.SUSPENDED,
                SaleStatus.ENDED,
            )
        private val INSPECTION_STATUSES =
            listOf(
                InspectionStatus.APPROVED,
                InspectionStatus.APPROVED,
                InspectionStatus.APPROVED,
                InspectionStatus.APPROVED,
                InspectionStatus.APPROVED,
                InspectionStatus.PENDING,
                InspectionStatus.REJECTED,
            )
    }
}
