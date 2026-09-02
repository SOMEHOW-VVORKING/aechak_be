package com.aechak.api.review

import com.aechak.api.support.KafkaIntegrationTestBase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.enums.ReviewStatus
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.websecurity.config.JwtConfig
import com.jayway.jsonpath.JsonPath
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant

/**
 * 리뷰 삭제 API(DELETE /reviews/{id}) 통합 테스트.
 * 삭제가 평점 재집계 이벤트를 아웃박스로 발행하므로 Flyway·임베디드 Kafka가 있는 KafkaIntegrationTestBase를 상속한다.
 */
@EmbeddedKafka(
    partitions = 1,
    topics = [
        com.aechak.infra.kafka.Topics.ORDER,
        com.aechak.infra.kafka.Topics.ORDER_DLT,
        com.aechak.infra.kafka.Topics.REVIEW,
        com.aechak.infra.kafka.Topics.REVIEW_DLT,
    ],
)
class ReviewDeleteIntegrationTest : KafkaIntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var jwtEncoder: JwtEncoder

    @PersistenceContext
    private lateinit var em: EntityManager

    private lateinit var mockMvc: MockMvc
    private val defaultSellerId = 77L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `리뷰 작성자가 삭제를 요청하면 204를 반환하고 DELETED 상태와 삭제 시각을 기록한다`() {
        val ownerId = createActiveUser()
        val reviewId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                persistReview(product.id, ownerId, orderItemId = 1L).id
            }!!

        mockMvc
            .perform(delete("/api/v1/reviews/$reviewId").header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
            .andExpect(status().isNoContent)

        val review = findReview(reviewId)
        assertEquals(ReviewStatus.DELETED, review.reviewStatus)
        assertNotNull(review.deletedAt)
    }

    @Test
    fun `작성자가 아닌 사용자가 삭제를 요청하면 403을 반환하고 상태와 삭제 시각을 변경하지 않는다`() {
        val ownerId = createActiveUser()
        val attackerId = createActiveUser()
        val reviewId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                persistReview(product.id, ownerId, orderItemId = 1L).id
            }!!

        val body =
            mockMvc
                .perform(delete("/api/v1/reviews/$reviewId").header(HttpHeaders.AUTHORIZATION, bearer(attackerId)))
                .andExpect(status().isForbidden)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        assertEquals(110003, JsonPath.read<Int>(body, "$.errorCode"))

        val review = findReview(reviewId)
        assertEquals(ReviewStatus.PUBLIC, review.reviewStatus)
        assertNull(review.deletedAt)
    }

    @Test
    fun `없는 리뷰를 삭제하면 404를 반환한다`() {
        val userId = createActiveUser()

        val body =
            mockMvc
                .perform(delete("/api/v1/reviews/999999").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isNotFound)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        assertEquals(110000, JsonPath.read<Int>(body, "$.errorCode"))
    }

    @Test
    fun `이미 삭제된 본인 리뷰를 다시 삭제해도 204를 반환한다`() {
        val ownerId = createActiveUser()
        val reviewId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                persistReview(product.id, ownerId, orderItemId = 1L).id
            }!!

        repeat(2) {
            mockMvc
                .perform(delete("/api/v1/reviews/$reviewId").header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
                .andExpect(status().isNoContent)
        }

        val review = findReview(reviewId)
        assertEquals(ReviewStatus.DELETED, review.reviewStatus)
        assertNotNull(review.deletedAt)
    }

    @Test
    fun `인증 토큰이 없으면 401을 반환한다`() {
        mockMvc.perform(delete("/api/v1/reviews/1")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `삭제한 리뷰는 상품 리뷰 목록에서 사라진다`() {
        val ownerId = createActiveUser()
        val (publicId, reviewId) =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                val review = persistReview(product.id, ownerId, orderItemId = 1L, content = "삭제될 리뷰")
                product.publicId to review.id
            }!!

        val before = getReviews(publicId, ownerId)
        assertEquals(1, JsonPath.read<List<*>>(before, "$.data.reviews").size)

        mockMvc
            .perform(delete("/api/v1/reviews/$reviewId").header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
            .andExpect(status().isNoContent)

        val after = getReviews(publicId, ownerId)
        assertEquals(0, JsonPath.read<List<*>>(after, "$.data.reviews").size)
    }

    private fun bearer(userId: Long) = "Bearer ${mintAccessToken(userId)}"

    private fun mintAccessToken(userId: Long): String {
        val now = Instant.now()
        val claims =
            JwtClaimsSet
                .builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim(JwtConfig.ROLE_CLAIM, "GENERAL")
                .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    private fun createActiveUser(): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            em.flush()
            em
                .createQuery("update User u set u.status = :st where u.id = :id")
                .setParameter("st", UserStatus.ACTIVE)
                .setParameter("id", user.id)
                .executeUpdate()
            user.id
        }!!

    private fun getReviews(
        publicId: String,
        userId: Long,
    ): String =
        mockMvc
            .perform(get("/api/v1/products/$publicId/reviews").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun findReview(reviewId: Long): Review =
        tx.execute {
            em.clear()
            em.find(Review::class.java, reviewId)
        }!!

    private fun persistVisibleProduct(name: String): Product {
        val root = Category.create(null, 1, "강아지", null, 1)
        em.persist(root)
        val mid = Category.create(root, 2, "사료간식", null, 1)
        em.persist(mid)
        if (em.find(Seller::class.java, defaultSellerId) == null) {
            em.persist(Seller.open(userId = defaultSellerId, storeName = "store-$defaultSellerId", baseShippingFee = 3000L))
        }
        val product =
            Product.register(
                category = mid,
                sellerId = defaultSellerId,
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

    private fun persistReview(
        productId: Long,
        authorUserId: Long,
        orderItemId: Long,
        rating: Int = 5,
        content: String = "좋은 상품입니다",
    ): Review {
        val review =
            Review.write(
                productId = productId,
                optionNameSnapshot = "블랙 / L",
                orderItemId = orderItemId,
                authorUserId = authorUserId,
                rating = rating,
                content = content,
            )
        em.persist(review)
        return review
    }
}
