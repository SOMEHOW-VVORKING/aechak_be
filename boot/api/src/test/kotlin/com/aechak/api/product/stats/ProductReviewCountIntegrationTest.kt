package com.aechak.api.product.stats

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.stats.usecase.ProductStatsUseCase
import com.aechak.application.product.stats.usecase.command.ApplyReviewStatsCommand
import com.aechak.application.review.usecase.ReviewRatingUseCase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.review.review.Review
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

/**
 * 리뷰 통계 반영이 검색 인기순용 products.review_count를 쓰는 경로를 본다.
 * 깨지면 인기순이 리뷰 수를 잘못 읽거나, 셀러 수정과 통계 반영이 서로의 값을 덮어쓰거나 낙관적 락 충돌을 낸다.
 */
class ProductReviewCountIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var reviewRatingUseCase: ReviewRatingUseCase

    @Autowired
    private lateinit var productStatsUseCase: ProductStatsUseCase

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `평점 재계산은 product_stats와 같은 리뷰 수를 products에 쓰고 상품 버전은 올리지 않는다`() {
        val product = persistProduct()
        persistReviews(product.id, ratings = listOf(5, 4, 3))

        reviewRatingUseCase.recomputeProductRating(product.id)

        assertEquals(3, productReviewCount(product.id))
        assertEquals(3, statsReviewCount(product.id))
        assertEquals(product.version, productVersion(product.id), "통계 반영은 셀러 수정의 낙관적 락과 부딪히지 않아야 한다")
    }

    @Test
    fun `같은 재계산이 중복 전달돼도 리뷰 수는 노출 리뷰 수와 같다`() {
        val product = persistProduct()
        persistReviews(product.id, ratings = listOf(5, 5))

        reviewRatingUseCase.recomputeProductRating(product.id)
        reviewRatingUseCase.recomputeProductRating(product.id)
        assertEquals(2, productReviewCount(product.id))

        persistReviews(product.id, ratings = listOf(1), orderItemOffset = 10)
        reviewRatingUseCase.recomputeProductRating(product.id)
        reviewRatingUseCase.recomputeProductRating(product.id)
        assertEquals(3, productReviewCount(product.id))
        assertEquals(3, statsReviewCount(product.id))
    }

    @Test
    fun `셀러 수정은 읽은 뒤 반영된 리뷰 수를 덮어쓰지 않고 버전만 하나 올린다`() {
        val product = persistProduct()
        val requiresNew =
            TransactionTemplate(transactionManager).apply {
                propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
            }

        tx.execute {
            val loaded = em.find(Product::class.java, product.id)
            // 셀러 트랜잭션이 상품을 읽은 뒤 통계 반영이 먼저 커밋된다
            requiresNew.execute {
                productStatsUseCase.applyReviewStats(
                    ApplyReviewStatsCommand(product.id, reviewCount = 5, ratingSum = 20L, averageRating = BigDecimal("4.00")),
                )
            }
            loaded.edit(
                category = loaded.category,
                name = "수정한 상품명",
                description = loaded.description,
                regularPrice = loaded.regularPrice,
                discountPrice = loaded.discountPrice,
                discountStartAt = loaded.discountStartAt,
                discountEndAt = loaded.discountEndAt,
            )
        }

        assertEquals(5, productReviewCount(product.id), "Hibernate UPDATE에 review_count가 없어야 한다")
        assertEquals(product.version + 1, productVersion(product.id))
        assertEquals("수정한 상품명", tx.execute { em.find(Product::class.java, product.id).name })
    }

    private fun persistProduct(): Product =
        tx.execute {
            val root = Category.create(null, 1, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, 2, "사료간식", null, 1)
            em.persist(mid)
            val product =
                Product.register(
                    category = mid,
                    sellerId = 77L,
                    name = "리뷰 수 상품",
                    description = null,
                    representativeImageKey = null,
                    regularPrice = 10000L,
                    discountPrice = null,
                    discountStartAt = null,
                    discountEndAt = null,
                )
            em.persist(product)
            product
        }!!

    private fun persistReviews(
        productId: Long,
        ratings: List<Int>,
        orderItemOffset: Int = 0,
    ) {
        tx.execute {
            ratings.forEachIndexed { index, rating ->
                em.persist(
                    Review.write(
                        productId = productId,
                        optionNameSnapshot = "블랙 / L",
                        orderItemId = productId * 100 + orderItemOffset + index, // 1주문1리뷰 UNIQUE 회피용 고유값
                        authorUserId = 1L,
                        rating = rating,
                        content = "좋은 상품입니다",
                    ),
                )
            }
        }
    }

    private fun productReviewCount(productId: Long): Int = scalarInt("SELECT review_count FROM products WHERE id = $productId")

    private fun statsReviewCount(productId: Long): Int = scalarInt("SELECT review_count FROM product_stats WHERE product_id = $productId")

    private fun productVersion(productId: Long): Int = scalarInt("SELECT version FROM products WHERE id = $productId")

    private fun scalarInt(sql: String): Int = tx.execute { (em.createNativeQuery(sql).singleResult as Number).toInt() }!!
}
