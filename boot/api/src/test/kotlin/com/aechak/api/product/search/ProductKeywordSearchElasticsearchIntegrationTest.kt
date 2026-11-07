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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 키워드 검색의 Elasticsearch 어댑터가 색인 재작성 뒤 JPA 어댑터와 같은 결과를 내는지 고정하는 통합 테스트
 * 같은 서비스에 어댑터만 바꿔 끼워 커서 앵커를 서비스가 만드는 그대로 따라가며 모든 페이지를 비교한다
 */
class ProductKeywordSearchElasticsearchIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Autowired
    private lateinit var jpaAdapter: ProductKeywordSearchQueryAdapter

    @Autowired
    private lateinit var esAdapter: ElasticsearchProductKeywordSearchAdapter

    @Autowired
    private lateinit var reindexService: ProductSearchReindexService

    @Test
    fun `색인 재작성 뒤 Elasticsearch 어댑터가 검색어, 정렬, 필터, 커서 순회 전체에서 JPA 어댑터와 같은 결과를 낸다`() {
        val (mid, productCount) = persistFixture()
        assertEquals(productCount.toLong(), reindexService.reindexAll(), "노출 여부와 무관하게 모든 상품을 색인한다")
        val jpa = ProductKeywordSearchService(jpaAdapter, categoryRepository)
        val es = ProductKeywordSearchService(esAdapter, categoryRepository)
        val filters: List<(ProductKeywordSearchQuery) -> ProductKeywordSearchQuery> =
            listOf(
                { it },
                { it.copy(minPrice = 10000L, maxPrice = 15000L) },
                { it.copy(categoryId = mid.id) },
                { it.copy(freeShipping = true) },
                { it.copy(minRating = BigDecimal("3.5")) },
                { it.copy(excludeSoldOut = true) },
            )

        for (keyword in KEYWORDS) {
            for (sort in ProductKeywordSearchSort.entries) {
                filters.forEachIndexed { i, filter ->
                    val query = filter(ProductKeywordSearchQuery(keyword = keyword, sort = sort, size = 3))
                    val expected = traverse(jpa, query)
                    assertEquals(expected, traverse(es, query), "$keyword $sort 필터 $i")
                    if (keyword != "없는상품" && i == 0) assertTrue(expected.first().items.isNotEmpty(), "$keyword: 픽스처가 일치 상품을 가져야 한다")
                }
            }
        }
        val multiPage = traverse(jpa, ProductKeywordSearchQuery(keyword = "강아지", size = 3))
        assertTrue(multiPage.size > 3, "커서로 여러 페이지를 넘겨야 한다")

        // 할인 시작과 종료(45번, 50번)가 검색 시각과 같은 한 시간 안에 있을 때도 낮은 가격순과 가격 필터가 같아야 한다
        val midHour = NOW.plusMinutes(30)
        for (filter in listOf(filters[0], filters[1])) {
            val query = filter(ProductKeywordSearchQuery(keyword = "강아지", sort = ProductKeywordSearchSort.PRICE_ASC, size = 3))
            assertEquals(traverse(jpa, query, midHour), traverse(es, query, midHour), "한 시간 안의 할인 경계")
        }
    }

    @Test
    fun `공개 id로 찾은 내부 id가 JPA 어댑터와 같다`() {
        persistFixture()
        reindexService.reindexAll()
        val publicIds = tx.execute { em.createQuery("select p.publicId from Product p", String::class.java).resultList }!!

        publicIds.forEach { assertEquals(jpaAdapter.findIdByPublicId(it), esAdapter.findIdByPublicId(it), it) }
        assertNull(esAdapter.findIdByPublicId("01JD5G7HJK8M2N4PQ6RSTV9WXY"))
    }

    // --- helpers ---

    /** 첫 페이지부터 커서를 따라 마지막 페이지까지 읽음 */
    private fun traverse(
        service: ProductKeywordSearchService,
        query: ProductKeywordSearchQuery,
        now: LocalDateTime = NOW,
    ): List<Page> {
        val pages = mutableListOf<Page>()
        var cursor: String? = null
        do {
            val result = tx.execute { service.searchPage(query.copy(cursor = cursor), now) }!!
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

    /**
     * 셀러 넷(유료 배송, 무료 배송, 정지, 행 없음), 활성 중분류 둘과 비활성 중분류 하나에 상품 60개
     * 이름에 특수문자, 대문자 라틴 글자, 긴 문구를 섞고 가격, 리뷰 수, 할인 기간(진행, 종료, 시작 전, 종료일 없음, 할인 없음, 경계 시각, 한 시간 안의 시작과 종료),
     * 판매 상태, 검수 상태, 통계 유무를 섞고 같은 정렬 키를 일부러 만든다
     */
    private fun persistFixture(): Pair<Category, Int> =
        tx.execute {
            persistSeller(SELLER, baseShippingFee = 3000L)
            persistSeller(FREE_SELLER, baseShippingFee = 0L)
            persistSeller(SUSPENDED_SELLER, baseShippingFee = 0L)
            val mid = persistMidCategory("사료간식")
            val otherMid = persistMidCategory("용품")
            val inactiveMid = persistMidCategory("단종")
            val products =
                (0 until PRODUCT_COUNT).map { i ->
                    val name =
                        listOfNotNull(
                            if (i % 8 == 2) "+강아지 -사료" else null,
                            if (i % 5 < 3) "강아지" else "고양이",
                            when (i % 5) {
                                0, 1 -> "사료"
                                2 -> "간식"
                                else -> "장난감"
                            },
                            when (i % 8) {
                                0 -> "100%"
                                1 -> "강아지_장난감"
                                3 -> "\"연어\" 트릿"
                                4 -> "Premium DOG"
                                else -> null
                            },
                            if (i % 13 == 5) LONG_NAME else null,
                            "$i",
                        ).joinToString(" ")
                    val regular = 10000L + (i % 4) * 5000L
                    val discount = if (i % 6 < 4) regular - 5000L else null
                    val (start, end) =
                        when {
                            i == 20 -> NOW to NOW.plusDays(1)
                            i == 26 -> NOW.minusDays(1) to NOW
                            i == 32 -> NOW.plusNanos(1_000) to null
                            i == 38 -> NOW.minusDays(1) to NOW.minusNanos(1_000)
                            i == 45 -> NOW.plusMinutes(10) to null
                            i == 50 -> NOW.minusDays(1) to NOW.plusMinutes(20)
                            i % 6 == 0 -> NOW.minusDays(1) to NOW.plusDays(1)
                            i % 6 == 1 -> NOW.minusDays(9) to NOW.minusDays(1)
                            i % 6 == 2 -> NOW.plusDays(1) to NOW.plusDays(9)
                            i % 6 == 3 -> NOW.minusDays(2) to null
                            else -> null to null
                        }
                    val sellerId =
                        when {
                            i == 47 -> MISSING_SELLER
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
                if (i % 7 == 6) setProductColumn(p.id, "saleStatus", SaleStatus.OUT_OF_STOCK)
                if (i % 11 == 10) setProductColumn(p.id, "saleStatus", SaleStatus.SUSPENDED)
                if (i % 13 == 12) setProductColumn(p.id, "inspectionStatus", InspectionStatus.PENDING)
                if (i % 4 != 3) {
                    persistStats(
                        p.id,
                        reviewCount = (i * 7) % 5,
                        averageRating =
                            BigDecimal("3.00").add(
                                BigDecimal("0.25").multiply(
                                    BigDecimal(i % 5),
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
            mid to products.size
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
        productId: Long,
        column: String,
        value: Any,
    ) = em
        .createQuery("update Product p set p.$column = :value where p.id = :id")
        .setParameter("value", value)
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

    companion object {
        private const val SELLER = 301L
        private const val FREE_SELLER = 302L
        private const val SUSPENDED_SELLER = 303L
        private const val MISSING_SELLER = 304L
        private const val PRODUCT_COUNT = 60
        private const val MAX_PAGES = 40
        private const val LONG_NAME = "유기농 국내산 연어와 고구마를 듬뿍 넣은 노령견 전용 저알러지 소프트 사료"
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 11, 5, 12, 0)
        private val KEYWORDS =
            listOf(
                "강아지",
                "사료",
                "강아지 사료",
                "사",
                "%",
                "_",
                "+강아지 -사료",
                "\"연어\"",
                "dog",
                "연어와 고구마를 듬뿍 넣은 노령견 전용",
                "없는상품",
            )
    }
}
