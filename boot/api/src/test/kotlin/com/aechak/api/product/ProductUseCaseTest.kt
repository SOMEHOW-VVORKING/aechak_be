package com.aechak.api.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.port.ProductCatalogSort
import com.aechak.application.product.service.ProductService
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합 테스트 — 노출 정책·카테고리 서브트리 필터·정렬 2종·커서 순회·할인 계산·교차 BC(셀러명)·통계 매핑을
 * 실 배선(실 MySQL 컨테이너)으로 검증한다. 격리는 커밋 + truncate(IntegrationTestBase) — 픽스처는 tx.execute로 실제 커밋한다.
 * 엔티티에 상태 세터가 없어 상태 픽스처는 persist 후 JPQL bulk update로 만든다(원자 UPDATE 정책과 동일 경로).
 */
class ProductUseCaseTest : IntegrationTestBase() {
    @Autowired
    lateinit var productUseCase: ProductUseCase

    /** 시각 주입이 필요한 커서 드리프트 검증 전용 — 그 외 테스트는 UseCase 경유가 기본. */
    @Autowired
    lateinit var productService: ProductService

    private val defaultSellerId = 77L

    // ---------- 픽스처 헬퍼 (tx.execute 안에서만 호출) ----------

    private fun persistMidCategory(name: String = "산책용품"): Category {
        val root = Category.create(null, 1, "강아지", null, 1)
        em.persist(root)
        val mid = Category.create(root, 2, name, null, 1)
        em.persist(mid)
        return mid
    }

    private fun persistLeafCategory(mid: Category): Category {
        val leaf = Category.create(mid, 3, "${mid.name}-소분류", null, 1)
        em.persist(leaf)
        return leaf
    }

    private fun persistProduct(
        category: Category,
        name: String,
        regular: Long = 10000L,
        discount: Long? = null,
        start: LocalDateTime? = null,
        end: LocalDateTime? = null,
        sellerId: Long = defaultSellerId,
        ensureSeller: Boolean = true, // 노출 조건에 셀러 ACTIVE가 추가돼 기본으로 셀러 행을 보장한다. 고아 상품 검증만 false.
    ): Product {
        if (ensureSeller) ensureActiveSeller(sellerId)
        val product =
            Product.register(
                category = category,
                sellerId = sellerId,
                name = name,
                description = null,
                representativeImageKey = "products/$name.jpg",
                regularPrice = regular,
                discountPrice = discount,
                discountStartAt = start,
                discountEndAt = end,
            )
        em.persist(product)
        return product
    }

    /** 셀러 행 find-or-create(기본 ACTIVE). 이미 있으면(예: storeName 지정 셀러) 그대로 둔다. */
    private fun ensureActiveSeller(sellerId: Long) {
        if (em.find(Seller::class.java, sellerId) == null) {
            em.persist(Seller.open(userId = sellerId, storeName = "store-$sellerId", baseShippingFee = 3000L))
        }
    }

    /** 엔티티에 상태 세터가 없으므로 bulk update로 우회 — 호출 전 em.flush()로 INSERT를 먼저 내보낸다. */
    private fun overrideSaleStatus(
        productId: Long,
        status: SaleStatus,
    ) {
        em
            .createQuery("update Product p set p.saleStatus = :status where p.id = :id")
            .setParameter("status", status)
            .setParameter("id", productId)
            .executeUpdate()
    }

    private fun overrideInspectionStatus(
        productId: Long,
        status: InspectionStatus,
    ) {
        em
            .createQuery("update Product p set p.inspectionStatus = :status where p.id = :id")
            .setParameter("status", status)
            .setParameter("id", productId)
            .executeUpdate()
    }

    private fun persistSeller(
        sellerId: Long,
        storeName: String,
    ) {
        em.persist(Seller.open(userId = sellerId, storeName = storeName, baseShippingFee = 3000L))
    }

    private fun overrideSellerStatus(
        sellerId: Long,
        status: SellerStatus,
    ) {
        em
            .createQuery("update Seller s set s.status = :status where s.userId = :id")
            .setParameter("status", status)
            .setParameter("id", sellerId)
            .executeUpdate()
    }

    private fun overrideCategoryStatus(
        categoryId: Long,
        status: CategoryStatus,
    ) {
        em
            .createQuery("update Category c set c.status = :status where c.id = :id")
            .setParameter("status", status)
            .setParameter("id", categoryId)
            .executeUpdate()
    }

    private fun persistStats(
        productId: Long,
        reviewCount: Int,
        averageRating: BigDecimal,
    ) {
        em.persist(ProductStats.init(productId))
        em.flush()
        em
            .createQuery(
                "update ProductStats s set s.reviewCount = :reviewCount, s.averageRating = :averageRating where s.productId = :id",
            ).setParameter("reviewCount", reviewCount)
            .setParameter("averageRating", averageRating)
            .setParameter("id", productId)
            .executeUpdate()
    }

    private fun search(
        categoryId: Long? = null,
        sort: ProductCatalogSort = ProductCatalogSort.LATEST,
        cursor: String? = null,
        size: Int = 20,
    ) = productUseCase.getProducts(ProductSearchQuery(categoryId, sort, cursor, size))

    // ---------- 노출 정책 ----------

    @Test
    fun `승인되고 판매중이거나 품절인 상품만 최신순으로 반환한다`() {
        val onSalePublicId =
            tx.execute {
                val mid = persistMidCategory()
                val onSale = persistProduct(mid, "판매중")
                val outOfStock = persistProduct(mid, "품절")
                val suspended = persistProduct(mid, "판매중지")
                val ended = persistProduct(mid, "판매종료")
                val pending = persistProduct(mid, "검수대기")
                em.flush()
                overrideSaleStatus(outOfStock.id, SaleStatus.OUT_OF_STOCK)
                overrideSaleStatus(suspended.id, SaleStatus.SUSPENDED)
                overrideSaleStatus(ended.id, SaleStatus.ENDED)
                overrideInspectionStatus(pending.id, InspectionStatus.PENDING)
                onSale.publicId
            }!!

        val result = search()

        assertEquals(listOf("품절", "판매중"), result.items.map { it.name }) // id desc = 등록 역순
        assertEquals(2L, result.totalCount)
        assertEquals(SaleStatus.OUT_OF_STOCK, result.items.first { it.name == "품절" }.saleStatus)
        assertEquals(onSalePublicId, result.items.last().productId) // 응답 식별자는 publicId
    }

    @Test
    fun `셀러가 ACTIVE가 아니거나 셀러 행이 없는 상품은 제외된다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "활성셀러", sellerId = 11L) // ensureActiveSeller로 ACTIVE 셀러 생성
            SellerStatus.entries
                .filterNot { it == SellerStatus.ACTIVE }
                .forEachIndexed { index, status ->
                    val sellerId = 20L + index
                    persistProduct(mid, "${status.name}셀러", sellerId = sellerId)
                    em.flush()
                    overrideSellerStatus(sellerId, status)
                }
            persistProduct(mid, "고아상품", sellerId = 99L, ensureSeller = false) // 셀러 행 없음
        }

        val result = search()

        assertEquals(listOf("활성셀러"), result.items.map { it.name })
        assertEquals(1L, result.totalCount)
    }

    @Test
    fun `상품 카테고리나 상위 조상이 비활성이면 제외된다`() {
        // 대>중>소 체인이 전부 ACTIVE면 소분류 상품이 노출된다(회귀).
        tx.execute {
            val leaf = persistLeafCategory(persistMidCategory("정상트리"))
            persistProduct(leaf, "정상노출")
        }
        assertTrue(search(size = 100).items.map { it.name }.contains("정상노출"))

        // self(소)·parent(중)·grandparent(대) 중 하나라도 비활성이면 그 소분류 상품은 빠진다.
        assertExcludedWhenInactive(CategoryTarget.SELF)
        assertExcludedWhenInactive(CategoryTarget.PARENT)
        assertExcludedWhenInactive(CategoryTarget.GRANDPARENT)
    }

    private enum class CategoryTarget { SELF, PARENT, GRANDPARENT }

    /** 케이스마다 독립된 대>중>소 트리를 만들고 target 노드만 INACTIVE로 바꿔, 그 소분류 상품이 제외되는지 확인. */
    private fun assertExcludedWhenInactive(target: CategoryTarget) {
        tx.execute {
            val mid = persistMidCategory("트리-$target")
            val leaf = persistLeafCategory(mid)
            persistProduct(leaf, "상품-$target")
            em.flush()
            val targetId =
                when (target) {
                    CategoryTarget.SELF -> leaf.id
                    CategoryTarget.PARENT -> mid.id
                    CategoryTarget.GRANDPARENT -> mid.parent!!.id
                }
            overrideCategoryStatus(targetId, CategoryStatus.INACTIVE)
        }
        val names = search(size = 100).items.map { it.name }
        assertFalse(names.contains("상품-$target"), "$target 비활성 시 상품이 제외돼야 한다")
    }

    // ---------- 카테고리 필터 ----------

    @Test
    fun `중분류로 필터하면 자신과 하위 소분류의 상품을 함께 반환한다`() {
        val midId =
            tx.execute {
                val mid = persistMidCategory("산책용품")
                val leaf = persistLeafCategory(mid)
                val otherMid = persistMidCategory("사료간식")
                persistProduct(mid, "중분류직결")
                persistProduct(leaf, "소분류소속")
                persistProduct(otherMid, "다른중분류")
                mid.id
            }!!

        val result = search(categoryId = midId)

        assertEquals(setOf("중분류직결", "소분류소속"), result.items.map { it.name }.toSet())
        assertEquals(2L, result.totalCount)
    }

    @Test
    fun `존재하지 않거나 비활성 카테고리로 조회하면 CATEGORY_NOT_FOUND 예외가 발생한다`() {
        val inactiveMidId =
            tx.execute {
                val mid = persistMidCategory()
                em.flush()
                em
                    .createQuery(
                        "update Category c set c.status = com.aechak.domain.product.category.enums.CategoryStatus.INACTIVE " +
                            "where c.id = :id",
                    ).setParameter("id", mid.id)
                    .executeUpdate()
                mid.id
            }!!

        assertCategoryNotFound { search(categoryId = 999_999L) }
        assertCategoryNotFound { search(categoryId = inactiveMidId) }
    }

    private fun assertCategoryNotFound(block: () -> Unit) {
        try {
            block()
            throw AssertionError("CATEGORY_NOT_FOUND가 발생해야 한다")
        } catch (e: BusinessException) {
            assertEquals(ProductErrorCode.CATEGORY_NOT_FOUND, e.errorCode)
        }
    }

    @Test
    fun `중분류가 아닌 카테고리 필터는 INVALID_CATEGORY_FILTER로 거절한다`() {
        val ids =
            tx.execute {
                val mid = persistMidCategory()
                val leaf = persistLeafCategory(mid)
                mid.parent!!.id to leaf.id
            }!!

        assertInvalidCategoryFilter { search(categoryId = ids.first) } // 대분류 — 소분류 상품이 조용히 빠지는 부분 결과 차단
        assertInvalidCategoryFilter { search(categoryId = ids.second) } // 소분류 — 계약(중분류) 밖
    }

    private fun assertInvalidCategoryFilter(block: () -> Unit) {
        try {
            block()
            throw AssertionError("INVALID_CATEGORY_FILTER가 발생해야 한다")
        } catch (e: BusinessException) {
            assertEquals(ProductErrorCode.INVALID_CATEGORY_FILTER, e.errorCode)
        }
    }

    // ---------- 커서 페이지네이션 ----------

    @Test
    fun `latest 커서로 다음 페이지를 겹침 없이 끝까지 순회한다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..5).forEach { persistProduct(mid, "상품$it") }
        }

        val page1 = search(size = 2)
        assertEquals(listOf("상품5", "상품4"), page1.items.map { it.name })
        assertTrue(page1.hasNext)

        val page2 = search(size = 2, cursor = page1.nextCursor)
        assertEquals(listOf("상품3", "상품2"), page2.items.map { it.name })
        assertTrue(page2.hasNext)

        val page3 = search(size = 2, cursor = page2.nextCursor)
        assertEquals(listOf("상품1"), page3.items.map { it.name })
        assertFalse(page3.hasNext)
        assertNull(page3.nextCursor)

        val walked = (page1.items + page2.items + page3.items).map { it.productId }
        assertEquals(5, walked.distinct().size) // 누락도 중복도 없다
    }

    @Test
    fun `PRICE_ASC는 조회 기준 시각의 유효가격으로 정렬한다`() {
        tx.execute {
            val now = LocalDateTime.now()
            val mid = persistMidCategory()
            persistProduct(mid, "활성할인", regular = 10000L, discount = 5000L, start = now.minusDays(1), end = now.plusDays(1))
            persistProduct(mid, "만료할인", regular = 6000L, discount = 4000L, start = now.minusDays(2), end = now.minusDays(1))
            persistProduct(mid, "정가만", regular = 7000L)
        }

        val result = search(sort = ProductCatalogSort.PRICE_ASC)

        // 판매가: 활성할인 5000 < 만료할인 6000(정가 복귀) < 정가만 7000
        assertEquals(listOf("활성할인", "만료할인", "정가만"), result.items.map { it.name })
    }

    @Test
    fun `PRICE_ASC는 동일한 정렬 가격에서도 id 타이브레이크로 누락과 중복 없이 순회한다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..4).forEach { persistProduct(mid, "동가$it", regular = 5000L) }
        }

        val page1 = search(sort = ProductCatalogSort.PRICE_ASC, size = 2)
        val page2 = search(sort = ProductCatalogSort.PRICE_ASC, size = 2, cursor = page1.nextCursor)

        assertEquals(listOf("동가4", "동가3"), page1.items.map { it.name }) // 같은 가격은 id desc 타이브레이크
        assertEquals(listOf("동가2", "동가1"), page2.items.map { it.name })
        assertFalse(page2.hasNext)
    }

    @Test
    fun `잘못된 커서와 다른 정렬의 커서는 INVALID_CURSOR로 거절한다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..3).forEach { persistProduct(mid, "상품$it") }
        }

        assertInvalidCursor { search(cursor = "%%%깨진커서%%%") }

        val latestCursor = search(size = 1).nextCursor
        assertInvalidCursor { search(sort = ProductCatalogSort.PRICE_ASC, cursor = latestCursor) }
    }

    private fun assertInvalidCursor(block: () -> Unit) {
        try {
            block()
            throw AssertionError("INVALID_CURSOR가 발생해야 한다")
        } catch (e: BusinessException) {
            assertEquals(CommonErrorCode.INVALID_CURSOR, e.errorCode)
        }
    }

    @Test
    fun `PRICE_ASC는 첫 페이지 기준 시각으로 정렬해 할인 종료 후에도 누락과 중복 없이 순회한다`() {
        // 시각을 직접 주입해야 하는 회귀 테스트라 ProductService를 직접 부른다 — Facade는 now를 내부 채번한다.
        val discountEnd = LocalDateTime.now()
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "할인중", regular = 10000L, discount = 5000L, start = discountEnd.minusDays(1), end = discountEnd)
            persistProduct(mid, "중간가", regular = 6000L)
            persistProduct(mid, "고가", regular = 7000L)
        }
        val query = ProductSearchQuery(sort = ProductCatalogSort.PRICE_ASC, size = 2)

        // 1페이지는 할인 종료 1시간 전 시각 — 판매가 [할인중 5000, 중간가 6000]
        val page1 = productService.getVisiblePage(query, discountEnd.minusHours(1))
        assertEquals(listOf("할인중", "중간가"), page1.items.map { it.name })

        // 2페이지는 할인 종료 1시간 후 시각 — 커서의 앵커 시각이 가격 뷰를 1페이지 시각으로 고정한다
        val page2 = productService.getVisiblePage(query.copy(cursor = page1.nextCursor), discountEnd.plusHours(1))
        assertEquals(listOf("고가"), page2.items.map { it.name }) // 할인 만료된 앵커가 다시 나타나지 않는다
        assertFalse(page2.hasNext)
    }

    @Test
    fun `미래 anchorNow가 실린 PRICE_ASC 커서는 거절한다`() {
        val publicId =
            tx.execute {
                val mid = persistMidCategory()
                persistProduct(mid, "상품1").publicId
            }!!

        val futureMillis =
            LocalDateTime
                .now()
                .plusDays(1)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        // 무필터(categoryId=null) 조회라 카테고리 토큰은 all — 카테고리는 통과시키고 미래 시각만 검증되게 한다.
        val crafted =
            Base64.getUrlEncoder().withoutPadding().encodeToString("p:all:5000:$futureMillis:$publicId".toByteArray())

        // 예약 할인 가격을 정렬 순서로 미리 엿보는 시간 조작 커서 차단
        assertInvalidCursor { search(sort = ProductCatalogSort.PRICE_ASC, cursor = crafted) }
    }

    @Test
    fun `다른 카테고리 필터에서 받은 커서는 INVALID_CURSOR로 거절한다`() {
        val midId =
            tx.execute {
                val mid = persistMidCategory("카테고리A")
                (1..3).forEach { persistProduct(mid, "A상품$it") }
                mid.id
            }!!

        val cursorForCategory = search(categoryId = midId, size = 1).nextCursor

        // 같은 커서를 무필터(전체) 조회에 재사용 — 카테고리 불일치라 keyset이 다른 집합에 걸리는 조용한 오답 대신 400
        assertInvalidCursor { search(cursor = cursorForCategory) }
    }

    // ---------- 카드 필드 ----------

    @Test
    fun `상품 카드는 응답 시각에 활성인 할인만 할인가와 할인율로 반환한다`() {
        tx.execute {
            val now = LocalDateTime.now()
            val mid = persistMidCategory()
            persistProduct(mid, "활성", regular = 20000L, discount = 14900L, start = now.minusHours(1), end = now.plusHours(1))
            persistProduct(mid, "만료", regular = 10000L, discount = 8000L, start = now.minusDays(2), end = now.minusDays(1))
        }

        val items = search().items.associateBy { it.name }

        assertEquals(14900L, items.getValue("활성").discountPrice)
        assertEquals(26, items.getValue("활성").discountRate) // 25.5% 반올림
        assertNull(items.getValue("만료").discountPrice) // 만료 할인가는 응답에 싣지 않는다
        assertNull(items.getValue("만료").discountRate)
        assertEquals(10000L, items.getValue("만료").regularPrice)
    }

    @Test
    fun `셀러명은 셀러의 스토어명으로 채워진다`() {
        tx.execute {
            persistSeller(defaultSellerId, storeName = "멍멍상회")
            val mid = persistMidCategory()
            persistProduct(mid, "상품", sellerId = defaultSellerId)
        }

        assertEquals("멍멍상회", search().items.single().sellerName)
    }

    @Test
    fun `통계가 없는 상품은 리뷰 0건과 별점 null로 반환된다`() {
        tx.execute {
            val mid = persistMidCategory()
            val rated = persistProduct(mid, "통계있음")
            persistProduct(mid, "통계없음")
            em.flush()
            persistStats(rated.id, reviewCount = 12, averageRating = BigDecimal("4.50"))
        }

        val items = search().items.associateBy { it.name }

        assertEquals(12, items.getValue("통계있음").reviewCount)
        assertEquals(0, BigDecimal("4.50").compareTo(items.getValue("통계있음").averageRating))
        assertEquals(0, items.getValue("통계없음").reviewCount)
        assertNull(items.getValue("통계없음").averageRating)
    }

    // ---------- totalCount ----------

    @Test
    fun `totalCount는 첫 페이지에만 실리고 이후 페이지는 null이다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..3).forEach { persistProduct(mid, "상품$it") }
        }

        val page1 = search(size = 2)
        assertEquals(3L, page1.totalCount)

        val page2 = search(size = 2, cursor = page1.nextCursor)
        assertNull(page2.totalCount)
    }
}
