package com.aechak.api.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.like.ProductLike
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.ProductImageType
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합 테스트 — 상세 조회의 노출 정책(목록과 동일 술어)·필드 매핑·이미지 정렬·중분류 유도·찜 여부를
 * 실 배선(실 MySQL 컨테이너)으로 검증한다. 상태 픽스처는 목록 테스트와 같은 JPQL bulk update 방식.
 * 이미지는 아직 도메인 생성 경로가 없어(등록 티켓 몫) 네이티브 INSERT로 픽스처를 만든다.
 */
class ProductDetailUseCaseTest : IntegrationTestBase() {
    @Autowired
    lateinit var productUseCase: ProductUseCase

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
        description: String? = null,
        regular: Long = 10000L,
        discount: Long? = null,
        start: LocalDateTime? = null,
        end: LocalDateTime? = null,
        sellerId: Long = defaultSellerId,
        ensureSeller: Boolean = true,
    ): Product {
        if (ensureSeller) ensureActiveSeller(sellerId)
        val product =
            Product.register(
                category = category,
                sellerId = sellerId,
                name = name,
                description = description,
                representativeImageKey = "products/$name.jpg",
                regularPrice = regular,
                discountPrice = discount,
                discountStartAt = start,
                discountEndAt = end,
            )
        em.persist(product)
        return product
    }

    private fun ensureActiveSeller(sellerId: Long) {
        if (em.find(Seller::class.java, sellerId) == null) {
            em.persist(Seller.open(userId = sellerId, storeName = "store-$sellerId", baseShippingFee = 3000L))
        }
    }

    /** 이미지는 루트 경유 생성 경로가 아직 없어 네이티브 INSERT로 만든다. */
    private fun insertImage(
        productId: Long,
        imageType: ProductImageType,
        storageKey: String,
        sortOrder: Int,
    ) {
        em
            .createNativeQuery(
                "insert into product_images " +
                    "(product_id, image_type, storage_key, sort_order, from_version_no, created_at, updated_at) " +
                    "values (:productId, :imageType, :storageKey, :sortOrder, 1, now(), now())",
            ).setParameter("productId", productId)
            .setParameter("imageType", imageType.name)
            .setParameter("storageKey", storageKey)
            .setParameter("sortOrder", sortOrder)
            .executeUpdate()
    }

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

    private fun overrideSellerProfile(
        sellerId: Long,
        profileImageKey: String,
        freeShippingThreshold: Long,
    ) {
        em
            .createQuery(
                "update Seller s set s.profileImageKey = :key, s.freeShippingThreshold = :threshold where s.userId = :id",
            ).setParameter("key", profileImageKey)
            .setParameter("threshold", freeShippingThreshold)
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
        em.persist(ProductStats.create(productId))
        em.flush()
        em
            .createQuery(
                "update ProductStats s set s.reviewCount = :reviewCount, s.averageRating = :averageRating where s.productId = :id",
            ).setParameter("reviewCount", reviewCount)
            .setParameter("averageRating", averageRating)
            .setParameter("id", productId)
            .executeUpdate()
    }

    private fun getDetail(
        publicId: String,
        userId: Long? = null,
    ) = productUseCase.getProduct(publicId, userId)

    private fun assertProductNotFound(block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(ProductErrorCode.PRODUCT_NOT_FOUND, exception.errorCode)
    }

    // ---------- 필드 매핑 ----------

    @Test
    fun `노출 조건을 충족하면 상세 필드가 완전한 형태로 반환된다`() {
        val publicId =
            tx.execute {
                val mid = persistMidCategory("사료간식")
                val product = persistProduct(mid, "연어사료", description = "연어와 고구마")
                em.flush()
                overrideSellerProfile(defaultSellerId, profileImageKey = "sellers/77.jpg", freeShippingThreshold = 30000L)
                product.publicId
            }!!

        val detail = getDetail(publicId)

        assertEquals(publicId, detail.productId)
        assertEquals("연어사료", detail.name)
        assertEquals("연어와 고구마", detail.description)
        assertEquals("products/연어사료.jpg", detail.representativeImageKey)
        assertEquals(10000L, detail.regularPrice)
        assertNull(detail.discountPrice)
        assertNull(detail.discountRate)
        assertEquals(SaleStatus.ON_SALE, detail.saleStatus)
        assertEquals(3000L, detail.shipping.baseShippingFee)
        assertEquals(30000L, detail.shipping.freeShippingThreshold)
        assertEquals("store-77", detail.seller.storeName)
        assertEquals("sellers/77.jpg", detail.seller.profileImageKey)
        assertFalse(detail.isLiked)
        assertTrue(detail.isPurchasable) // 판매중이므로 구매 가능
    }

    @Test
    fun `무료배송 임계값이 없는 셀러는 배송 정보에 null로 반환된다`() {
        val publicId =
            tx.execute {
                val mid = persistMidCategory()
                persistProduct(mid, "기본배송상품").publicId // Seller.open 기본값 — 임계값 미설정
            }!!

        val shipping = getDetail(publicId).shipping

        assertEquals(3000L, shipping.baseShippingFee)
        assertNull(shipping.freeShippingThreshold)
    }

    @Test
    fun `이미지는 sortOrder 순으로 타입과 함께 전부 반환된다`() {
        val publicId =
            tx.execute {
                val mid = persistMidCategory()
                val product = persistProduct(mid, "이미지상품")
                em.flush()
                // 대표 이미지 행은 등록이 이미 만들어 둔다
                insertImage(product.id, ProductImageType.DETAIL, "products/d1.jpg", 2)
                insertImage(product.id, ProductImageType.PRODUCT, "products/p1.jpg", 1)
                product.publicId
            }!!

        val images = getDetail(publicId).images

        assertEquals(listOf(0, 1, 2), images.map { it.sortOrder })
        assertEquals(
            listOf(ProductImageType.REPRESENTATIVE, ProductImageType.PRODUCT, ProductImageType.DETAIL),
            images.map { it.imageType },
        )
        assertEquals("products/이미지상품.jpg", images.first().storageKey)
    }

    @Test
    fun `할인 기간 중이면 할인가와 할인율, 만료면 null을 반환한다`() {
        val now = LocalDateTime.now()
        val ids =
            tx.execute {
                val mid = persistMidCategory()
                val active =
                    persistProduct(mid, "활성할인", regular = 20000L, discount = 14900L, start = now.minusHours(1), end = now.plusHours(1))
                val expired =
                    persistProduct(mid, "만료할인", regular = 10000L, discount = 8000L, start = now.minusDays(2), end = now.minusDays(1))
                active.publicId to expired.publicId
            }!!

        val active = getDetail(ids.first)
        assertEquals(14900L, active.discountPrice)
        assertEquals(26, active.discountRate)

        val expired = getDetail(ids.second)
        assertNull(expired.discountPrice)
        assertNull(expired.discountRate)
        assertEquals(10000L, expired.regularPrice)
    }

    @Test
    fun `통계가 없으면 리뷰 0건과 평점 null, 있으면 그대로 반환한다`() {
        val ids =
            tx.execute {
                val mid = persistMidCategory()
                val rated = persistProduct(mid, "통계있음")
                val unrated = persistProduct(mid, "통계없음")
                em.flush()
                persistStats(rated.id, reviewCount = 12, averageRating = BigDecimal("4.50"))
                rated.publicId to unrated.publicId
            }!!

        val rated = getDetail(ids.first).review
        assertEquals(12, rated.reviewCount)
        assertEquals(0, BigDecimal("4.50").compareTo(rated.averageRating))

        val unrated = getDetail(ids.second).review
        assertEquals(0, unrated.reviewCount)
        assertNull(unrated.averageRating)
    }

    @Test
    fun `품절 상품은 OUT_OF_STOCK 상태로 정상 노출되되 구매 불가로 내려간다`() {
        val publicId =
            tx.execute {
                val mid = persistMidCategory()
                val product = persistProduct(mid, "품절상품")
                em.flush()
                overrideSaleStatus(product.id, SaleStatus.OUT_OF_STOCK)
                product.publicId
            }!!

        val detail = getDetail(publicId)
        assertEquals(SaleStatus.OUT_OF_STOCK, detail.saleStatus)
        assertFalse(detail.isPurchasable) // 품절이라 상세는 볼 수 있어도 구매는 불가
    }

    // ---------- 카테고리 ----------

    @Test
    fun `카테고리 경로는 대분류부터 소분류까지 순서대로 반환되고 중분류가 목록 필터 대상이다`() {
        var midId = 0L
        val ids =
            tx.execute {
                val mid = persistMidCategory("사료간식")
                val leaf = persistLeafCategory(mid)
                midId = mid.id
                persistProduct(leaf, "소분류상품").publicId to persistProduct(mid, "중분류상품").publicId
            }!!

        // 소분류에 달린 상품 → 대>중>소 3단 경로
        val fromLeaf = getDetail(ids.first).categories
        assertEquals(listOf(1, 2, 3), fromLeaf.map { it.depth })
        assertEquals(listOf("강아지", "사료간식", "사료간식-소분류"), fromLeaf.map { it.name })
        assertEquals(midId, fromLeaf.single { it.depth == 2 }.categoryId) // 중분류 = 목록 필터가 받는 단위

        // 중분류에 달린 상품 → 대>중 2단 경로
        val fromMid = getDetail(ids.second).categories
        assertEquals(listOf(1, 2), fromMid.map { it.depth })
        assertEquals(midId, fromMid.single { it.depth == 2 }.categoryId)
    }

    @Test
    fun `대분류에 직결된 상품은 카테고리 경로가 대분류 하나다`() {
        val publicId =
            tx.execute {
                val root = Category.create(null, 1, "강아지", null, 1)
                em.persist(root)
                persistProduct(root, "루트직결상품").publicId
            }!!

        val categories = getDetail(publicId).categories

        assertEquals(1, categories.size)
        assertEquals(1, categories.single().depth)
        assertEquals("강아지", categories.single().name)
    }

    // ---------- 찜 여부 ----------

    @Test
    fun `찜한 사용자는 isLiked가 true고 찜하지 않은 사용자는 false다`() {
        val likerUserId = 42L
        val publicId =
            tx.execute {
                val mid = persistMidCategory()
                val product = persistProduct(mid, "찜상품")
                em.persist(ProductLike.of(product, likerUserId))
                product.publicId
            }!!

        assertTrue(getDetail(publicId, userId = likerUserId).isLiked)
        assertFalse(getDetail(publicId, userId = 99L).isLiked)
    }

    @Test
    fun `비로그인 조회는 isLiked가 false다`() {
        val publicId =
            tx.execute {
                val mid = persistMidCategory()
                val product = persistProduct(mid, "찜상품")
                em.persist(ProductLike.of(product, 42L))
                product.publicId
            }!!

        assertFalse(getDetail(publicId, userId = null).isLiked)
    }

    // ---------- 404 매트릭스 ----------

    @Test
    fun `존재하지 않는 publicId 조회는 PRODUCT_NOT_FOUND 예외가 발생한다`() {
        assertProductNotFound { getDetail("01JUNKNOWNXXXXXXXXXXXXXX00") }
    }

    @Test
    fun `검수 미승인 상품 조회는 PRODUCT_NOT_FOUND 예외가 발생한다`() {
        InspectionStatus.entries.filterNot { it == InspectionStatus.APPROVED }.forEach { status ->
            val publicId =
                tx.execute {
                    val mid = persistMidCategory("검수-$status")
                    val product = persistProduct(mid, "검수상품-$status")
                    em.flush()
                    overrideInspectionStatus(product.id, status)
                    product.publicId
                }!!
            assertProductNotFound { getDetail(publicId) }
        }
    }

    @Test
    fun `판매중지나 판매종료 상품 조회는 PRODUCT_NOT_FOUND 예외가 발생한다`() {
        listOf(SaleStatus.SUSPENDED, SaleStatus.ENDED).forEach { status ->
            val publicId =
                tx.execute {
                    val mid = persistMidCategory("판매-$status")
                    val product = persistProduct(mid, "판매상품-$status")
                    em.flush()
                    overrideSaleStatus(product.id, status)
                    product.publicId
                }!!
            assertProductNotFound { getDetail(publicId) }
        }
    }

    @Test
    fun `셀러가 ACTIVE가 아니거나 셀러 행이 없으면 PRODUCT_NOT_FOUND 예외가 발생한다`() {
        SellerStatus.entries.filterNot { it == SellerStatus.ACTIVE }.forEachIndexed { index, status ->
            val sellerId = 200L + index
            val publicId =
                tx.execute {
                    val mid = persistMidCategory("셀러-$status")
                    val product = persistProduct(mid, "셀러상품-$status", sellerId = sellerId)
                    em.flush()
                    overrideSellerStatus(sellerId, status)
                    product.publicId
                }!!
            assertProductNotFound { getDetail(publicId) }
        }

        val orphanPublicId =
            tx.execute {
                val mid = persistMidCategory("셀러-없음")
                persistProduct(mid, "고아상품", sellerId = 999L, ensureSeller = false).publicId
            }!!
        assertProductNotFound { getDetail(orphanPublicId) }
    }

    @Test
    fun `카테고리 자신이나 상위 조상이 비활성이면 PRODUCT_NOT_FOUND 예외가 발생한다`() {
        assertNotFoundWhenCategoryInactive(CategoryTarget.SELF)
        assertNotFoundWhenCategoryInactive(CategoryTarget.PARENT)
        assertNotFoundWhenCategoryInactive(CategoryTarget.GRANDPARENT)
    }

    private enum class CategoryTarget { SELF, PARENT, GRANDPARENT }

    private fun assertNotFoundWhenCategoryInactive(target: CategoryTarget) {
        val publicId =
            tx.execute {
                val mid = persistMidCategory("트리-$target")
                val leaf = persistLeafCategory(mid)
                val product = persistProduct(leaf, "상품-$target")
                em.flush()
                val targetId =
                    when (target) {
                        CategoryTarget.SELF -> leaf.id
                        CategoryTarget.PARENT -> mid.id
                        CategoryTarget.GRANDPARENT -> mid.parent!!.id
                    }
                overrideCategoryStatus(targetId, CategoryStatus.INACTIVE)
                product.publicId
            }!!
        assertProductNotFound { getDetail(publicId) }
    }
}
