package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.product.repository.ProductRepository
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.product.version.repository.ProductVersionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 영속 계약 통합 테스트. 수정 경로가 새로 쓰는 조회 두 개와 이미지 전체 교체를 실 DB에서 고정한다.
 * 깨지면 남의 상품이 수정 대상으로 잡히거나, 교체했다고 응답한 이미지가 옛 행과 함께 남는다.
 */
class SellerProductPersistenceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var productVersionRepository: ProductVersionRepository

    private var leafCategoryId = 0L

    @BeforeEach
    fun seedCategory() {
        tx.execute {
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            leafCategoryId = leaf.id
        }
    }

    @Test
    fun `소유자 조건 조회는 본인 상품만 돌려준다`() {
        val mine = persistProduct(sellerId = 1L)
        val others = persistProduct(sellerId = 2L)

        tx.execute {
            assertNotNull(
                productRepository.findByPublicIdAndSellerId(mine, 1L),
                "본인 상품은 조회돼야 한다",
            )
            assertNull(
                productRepository.findByPublicIdAndSellerId(others, 1L),
                "남의 상품은 없는 것과 같아야 한다",
            )
            assertNull(
                productRepository.findByPublicIdAndSellerId("01JZZZZZZZZZZZZZZZZZZZZZZZ", 1L),
                "없는 publicId는 null이어야 한다",
            )
        }
    }

    @Test
    fun `버전이 하나도 없으면 마지막 버전 번호는 null이다`() {
        val publicId = persistProduct(sellerId = 1L)

        tx.execute {
            assertNull(
                productVersionRepository.findLastVersionNo(productIdOf(publicId)),
                "승인본이 없으면 다음 번호를 1로 시작해야 하므로 null이어야 한다",
            )
        }
    }

    @Test
    fun `마지막 버전 번호는 삽입 순서가 아니라 번호 기준으로 고른다`() {
        val publicId = persistProduct(sellerId = 1L)
        val productId = tx.execute { productIdOf(publicId) }!!
        appendVersion(productId, versionNo = 1)
        appendVersion(productId, versionNo = 3)
        appendVersion(productId, versionNo = 2)

        val last = tx.execute { productVersionRepository.findLastVersionNo(productId) }

        assertEquals(3, last, "번호가 뒤섞여 들어와도 가장 큰 번호를 골라야 한다")
    }

    @Test
    fun `다른 상품의 버전은 마지막 번호 계산에 섞이지 않는다`() {
        val mine = persistProduct(sellerId = 1L)
        val others = persistProduct(sellerId = 2L)
        val myProductId = tx.execute { productIdOf(mine) }!!
        appendVersion(tx.execute { productIdOf(others) }!!, versionNo = 9)

        val last = tx.execute { productVersionRepository.findLastVersionNo(myProductId) }

        assertNull(last, "남의 상품 버전이 내 다음 번호를 밀어 올리면 안 된다")
    }

    @Test
    fun `이미지 교체를 커밋하면 옛 행이 노출 구간과 함께 남는다`() {
        val publicId =
            persistProduct(
                sellerId = 1L,
                additionalImageKeys = listOf("products/a1.png", "products/a2.png"),
                detailImageKeys = listOf("products/d1.png"),
            )

        tx.execute {
            val product = productRepository.findByPublicIdAndSellerId(publicId, 1L)!!
            product.replaceImages("products/new-thumb.png", listOf("products/a9.png"), emptyList(), versionNo = 2)
            productRepository.save(product)
        }

        val open =
            tx.execute {
                em
                    .createQuery(
                        "select i.storageKey from Product p join p._images i where i.toVersionNo is null order by i.id asc",
                        String::class.java,
                    ).resultList
            }!!
        assertEquals(listOf("products/new-thumb.png", "products/a9.png"), open, "현재 노출분은 보낸 목록 그대로여야 한다: $open")

        val closed =
            tx.execute {
                em
                    .createQuery(
                        "select i.storageKey, i.fromVersionNo, i.toVersionNo from Product p join p._images i " +
                            "where i.toVersionNo is not null order by i.id asc",
                        Array<Any>::class.java,
                    ).resultList
                    .map { it.toList() }
            }!!
        assertEquals(
            listOf(
                listOf("products/old-thumb.png", 1, 1),
                listOf("products/a1.png", 1, 1),
                listOf("products/a2.png", 1, 1),
                listOf("products/d1.png", 1, 1),
            ),
            closed,
            "빠진 행은 지워지지 않고 1버전 구간으로 닫혀야 한다: $closed",
        )
        assertEquals(
            "products/new-thumb.png",
            tx.execute { em.createQuery("select p.representativeImageKey from Product p", String::class.java).singleResult },
            "대표 이미지 캐시도 함께 갱신돼야 한다",
        )
    }

    private fun persistProduct(
        sellerId: Long,
        additionalImageKeys: List<String> = emptyList(),
        detailImageKeys: List<String> = emptyList(),
    ): String =
        tx.execute {
            val product =
                Product.register(
                    category = em.find(Category::class.java, leafCategoryId),
                    sellerId = sellerId,
                    name = "연어 건사료 2kg",
                    description = null,
                    representativeImageKey = "products/old-thumb.png",
                    regularPrice = 25_000L,
                    discountPrice = null,
                    discountStartAt = null,
                    discountEndAt = null,
                    additionalImageKeys = additionalImageKeys,
                    detailImageKeys = detailImageKeys,
                )
            em.persist(product)
            product.publicId
        }!!

    private fun appendVersion(
        productId: Long,
        versionNo: Int,
    ) {
        tx.execute {
            productVersionRepository.save(
                ProductVersion.snapshot(
                    product = em.getReference(Product::class.java, productId),
                    versionNo = versionNo,
                    nameSnapshot = "연어 건사료 2kg",
                    priceSnapshot = 25_000L,
                    statusSnapshot = SaleStatus.ON_SALE,
                    thumbnailKeySnapshot = "products/old-thumb.png",
                    changedBy = VersionChangedBy.SELLER,
                ),
            )
        }
    }

    private fun productIdOf(publicId: String): Long =
        em
            .createQuery("select p.id from Product p where p.publicId = :publicId", Long::class.javaObjectType)
            .setParameter("publicId", publicId)
            .singleResult
}
