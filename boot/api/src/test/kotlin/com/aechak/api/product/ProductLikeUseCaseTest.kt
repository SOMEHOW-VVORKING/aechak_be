package com.aechak.api.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.like.usecase.ProductLikeUseCase
import com.aechak.application.product.like.usecase.command.ProductLikeCommand
import com.aechak.application.product.like.usecase.query.LikedProductListQuery
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.seller.seller.Seller
import org.springframework.beans.factory.annotation.Autowired
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 찜 추가·취소와 내 찜 목록 통합 테스트 (실 MySQL) */
class ProductLikeUseCaseTest : IntegrationTestBase() {
    @Autowired
    lateinit var productLikeUseCase: ProductLikeUseCase

    private val defaultSellerId = 77L
    private val likerId = 42L
    private val otherUserId = 99L

    private fun doLike(
        publicId: String,
        userId: Long = likerId,
    ) = productLikeUseCase.like(ProductLikeCommand(publicId, userId))

    private fun doUnlike(
        publicId: String,
        userId: Long = likerId,
    ) = productLikeUseCase.unlike(ProductLikeCommand(publicId, userId))

    // ---------- 픽스처 헬퍼 ----------

    private fun persistMidCategory(name: String = "산책용품"): Category {
        val root = Category.create(null, 1, "강아지", null, 1)
        em.persist(root)
        val mid = Category.create(root, 2, name, null, 1)
        em.persist(mid)
        return mid
    }

    private fun ensureActiveSeller(sellerId: Long) {
        if (em.find(Seller::class.java, sellerId) == null) {
            em.persist(Seller.open(userId = sellerId, storeName = "store-$sellerId", baseShippingFee = 3000L))
        }
    }

    private fun persistProduct(
        category: Category,
        name: String,
        sellerId: Long = defaultSellerId,
    ): Product {
        ensureActiveSeller(sellerId)
        val product =
            Product.register(
                category = category,
                sellerId = sellerId,
                name = name,
                description = null,
                representativeImageKey = "products/$name.jpg",
                regularPrice = 10000L,
                discountPrice = null,
                discountStartAt = null,
                discountEndAt = null,
            )
        em.persist(product)
        return product
    }

    /** likeCount 검증용 집계 행 시드 */
    private fun persistStats(productId: Long) {
        em.persist(ProductStats.create(productId))
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

    private fun membershipCount(
        productId: Long,
        userId: Long,
    ): Long =
        tx.execute {
            em
                .createQuery(
                    "select count(pl) from ProductLike pl where pl.product.id = :pid and pl.userId = :uid",
                    Long::class.javaObjectType,
                ).setParameter("pid", productId)
                .setParameter("uid", userId)
                .singleResult
                .toLong()
        }!!

    private fun likeCountOf(productId: Long): Long =
        tx.execute {
            em
                .createQuery("select s.likeCount from ProductStats s where s.productId = :id", Long::class.javaObjectType)
                .setParameter("id", productId)
                .singleResult
                .toLong()
        }!!

    private fun likedPublicIds(
        userId: Long,
        cursor: String? = null,
        size: Int = 20,
    ) = productLikeUseCase.getLikedProducts(LikedProductListQuery(cursor = cursor, size = size), userId)

    // ---------- 찜 추가·취소 ----------

    @Test
    fun `찜하면 목록에 담기고 찜 수가 하나 늘어난다`() {
        val publicId =
            tx.execute {
                val product = persistProduct(persistMidCategory(), "연어사료")
                em.flush()
                persistStats(product.id)
                product.publicId
            }!!
        val productId = productIdOf(publicId)

        doLike(publicId, likerId)

        assertEquals(1L, membershipCount(productId, likerId))
        assertEquals(1L, likeCountOf(productId))
    }

    @Test
    fun `통계 행이 없는 상품을 찜해도 찜 수가 1로 집계된다`() {
        val publicId =
            tx.execute {
                persistProduct(persistMidCategory(), "통계없는상품").publicId
            }!!
        val productId = productIdOf(publicId)

        doLike(publicId, likerId)

        assertEquals(1L, membershipCount(productId, likerId))
        assertEquals(1L, likeCountOf(productId))
    }

    @Test
    fun `이미 찜한 상품을 다시 찜해도 찜은 하나만 남고 찜 수도 그대로다`() {
        val publicId =
            tx.execute {
                val product = persistProduct(persistMidCategory(), "중복찜상품")
                em.flush()
                persistStats(product.id)
                product.publicId
            }!!
        val productId = productIdOf(publicId)

        doLike(publicId, likerId)
        doLike(publicId, likerId)

        assertEquals(1L, membershipCount(productId, likerId))
        assertEquals(1L, likeCountOf(productId))
    }

    @Test
    fun `찜을 취소하면 목록에서 빠지고 찜 수가 하나 줄어든다`() {
        val publicId =
            tx.execute {
                val product = persistProduct(persistMidCategory(), "찜취소상품")
                em.flush()
                persistStats(product.id)
                product.publicId
            }!!
        val productId = productIdOf(publicId)
        doLike(publicId, likerId)

        doUnlike(publicId, likerId)

        assertEquals(0L, membershipCount(productId, likerId))
        assertEquals(0L, likeCountOf(productId))
    }

    @Test
    fun `찜하지 않은 상품을 취소하면 아무 일도 없고 찜 수도 그대로다`() {
        val publicId =
            tx.execute {
                val product = persistProduct(persistMidCategory(), "미찜상품")
                em.flush()
                persistStats(product.id)
                product.publicId
            }!!
        val productId = productIdOf(publicId)

        doUnlike(publicId, likerId)

        assertEquals(0L, membershipCount(productId, likerId))
        assertEquals(0L, likeCountOf(productId))
    }

    @Test
    fun `없는 상품을 찜하면 상품을 찾을 수 없다는 오류가 난다`() {
        val exception = assertFailsWith<BusinessException> { doLike("01JUNKNOWNXXXXXXXXXXXXXX00", likerId) }
        assertEquals(ProductErrorCode.PRODUCT_NOT_FOUND, exception.errorCode)
    }

    // ---------- 내 찜 목록 ----------

    @Test
    fun `내 찜 목록은 최근에 찜한 순서로 나온다`() {
        val publicIds =
            tx.execute {
                val mid = persistMidCategory()
                listOf("상품1", "상품2", "상품3").map { persistProduct(mid, it).publicId }
            }!!
        publicIds.forEach { doLike(it, likerId) } // 1, 2, 3 순서로 찜

        val page = likedPublicIds(likerId)

        assertEquals(listOf(publicIds[2], publicIds[1], publicIds[0]), page.items.map { it.productId })
        assertTrue(page.items.all { it.isLiked }, "찜 목록 카드는 모두 isLiked=true여야 한다")
        assertEquals(3L, page.totalCount)
        assertFalse(page.hasNext)
        assertNull(page.nextCursor)
    }

    @Test
    fun `내 찜 목록은 다음 페이지를 이어서 가져온다`() {
        val publicIds =
            tx.execute {
                val mid = persistMidCategory()
                listOf("페이지1", "페이지2", "페이지3").map { persistProduct(mid, it).publicId }
            }!!
        publicIds.forEach { doLike(it, likerId) }

        val first = likedPublicIds(likerId, size = 2)
        assertEquals(listOf(publicIds[2], publicIds[1]), first.items.map { it.productId })
        assertTrue(first.hasNext)
        assertEquals(3L, first.totalCount)

        val second = likedPublicIds(likerId, cursor = first.nextCursor, size = 2)
        assertEquals(listOf(publicIds[0]), second.items.map { it.productId })
        assertFalse(second.hasNext)
        assertNull(second.nextCursor)
        assertNull(second.totalCount) // 총개수는 첫 페이지에서만
    }

    @Test
    fun `품절 상품은 목록에 보이고 판매 중지 상품은 목록에서 빠져도 찜은 남는다`() {
        val ids =
            tx.execute {
                val mid = persistMidCategory()
                val soldOut = persistProduct(mid, "품절상품")
                val suspended = persistProduct(mid, "판매중지상품")
                soldOut.publicId to suspended.publicId
            }!!
        doLike(ids.first, likerId)
        doLike(ids.second, likerId)
        val suspendedId = productIdOf(ids.second)
        tx.execute {
            overrideSaleStatus(productIdOf(ids.first), SaleStatus.OUT_OF_STOCK)
            overrideSaleStatus(suspendedId, SaleStatus.SUSPENDED)
        }

        val page = likedPublicIds(likerId)

        assertEquals(listOf(ids.first), page.items.map { it.productId })
        val card = page.items.single()
        assertEquals(SaleStatus.OUT_OF_STOCK, card.saleStatus)
        assertTrue(card.isViewable) // 카테고리 활성이라 상세 진입은 가능
        assertFalse(card.isPurchasable) // 품절이라 구매는 불가
        assertEquals(1L, membershipCount(suspendedId, likerId))
    }

    @Test
    fun `다른 사람이 찜한 상품은 내 목록에 안 나온다`() {
        val publicIds =
            tx.execute {
                val mid = persistMidCategory()
                listOf("내찜", "남찜").map { persistProduct(mid, it).publicId }
            }!!
        doLike(publicIds[0], likerId)
        doLike(publicIds[1], otherUserId)

        val page = likedPublicIds(likerId)

        assertEquals(listOf(publicIds[0]), page.items.map { it.productId })
    }

    @Test
    fun `카테고리가 비활성으로 바뀐 찜 상품도 목록에 보이되 상세 페이지 진입 불가이다`() {
        val publicId =
            tx.execute {
                val mid = persistMidCategory()
                val product = persistProduct(mid, "카테고리비활성상품")
                em.flush()
                overrideCategoryStatus(mid.id, CategoryStatus.INACTIVE)
                product.publicId
            }!!
        doLike(publicId, likerId)

        val page = likedPublicIds(likerId)

        assertEquals(listOf(publicId), page.items.map { it.productId })
        val card = page.items.single()
        assertFalse(card.isViewable) // 카테고리 비활성이라 상세 진입 불가
        assertFalse(card.isPurchasable) // 진입 불가면 구매도 불가
    }

    @Test
    fun `상위 카테고리가 비활성이면 자기 카테고리가 활성이어도 상세 페이지 진입이 불가하다`() {
        val publicId =
            tx.execute {
                val mid = persistMidCategory()
                val product = persistProduct(mid, "부모카테고리비활성상품")
                em.flush()
                overrideCategoryStatus(mid.parent!!.id, CategoryStatus.INACTIVE)
                product.publicId
            }!!
        doLike(publicId, likerId)

        val card = likedPublicIds(likerId).items.single()

        assertFalse(card.isViewable)
        assertFalse(card.isPurchasable)
    }

    @Test
    fun `판매 중이고 카테고리가 활성인 찜 상품은 상세 페이지 진입과 구매가 모두 가능하다`() {
        val publicId =
            tx.execute {
                persistProduct(persistMidCategory(), "정상판매상품").publicId
            }!!
        doLike(publicId, likerId)

        val card = likedPublicIds(likerId).items.single()

        assertTrue(card.isViewable)
        assertTrue(card.isPurchasable)
    }

    // ---------- 동시성 ----------

    @Test
    fun `동시에 여러 번 찜해도 오류 없이 찜은 하나만 남고 찜 수도 하나다`() {
        val publicId =
            tx.execute {
                val product = persistProduct(persistMidCategory(), "동시찜상품")
                em.flush()
                persistStats(product.id)
                product.publicId
            }!!
        val productId = productIdOf(publicId)

        val threads = 6
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        try {
            val futures =
                (1..threads).map {
                    pool.submit {
                        ready.countDown()
                        start.await()
                        try {
                            doLike(publicId, likerId)
                        } catch (e: Throwable) {
                            errors.add(e)
                        }
                    }
                }
            ready.await(5, TimeUnit.SECONDS)
            start.countDown() // 모든 스레드가 동시에 출발
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertTrue(errors.isEmpty(), "동시에 찜할 때 오류가 나면 안 된다: $errors")
        assertEquals(1L, membershipCount(productId, likerId))
        assertEquals(1L, likeCountOf(productId))
    }

    private fun productIdOf(publicId: String): Long =
        tx.execute {
            em
                .createQuery("select p.id from Product p where p.publicId = :pid", Long::class.javaObjectType)
                .setParameter("pid", publicId)
                .singleResult
                .toLong()
        }!!
}
