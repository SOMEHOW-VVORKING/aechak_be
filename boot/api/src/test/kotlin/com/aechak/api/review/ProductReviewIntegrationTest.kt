package com.aechak.api.review

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.enums.ReviewStatus
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.user.user.User
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDateTime

/** 상품 리뷰 목록 API(GET /products/{publicId}/reviews) 통합 테스트. */
class ProductReviewIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private val defaultSellerId = 77L
    private var cachedToken: String? = null

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `상품 리뷰를 최신순으로 반환하고 평점 요약을 담는다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct("강아지 사료")
                em.flush()
                val coco = persistAuthor("코코")
                val choco = persistAuthor("초코")
                persistReview(product.id, coco, rating = 5, orderItemId = 1L, content = "정말 만족합니다")
                persistReview(product.id, choco, rating = 3, orderItemId = 2L, content = "무난해요")
                product.publicId
            }!!

        val body = getReviews(publicId)

        // 최신순 = id desc = 등록 역순
        assertEquals(listOf("초코", "코코"), JsonPath.read<List<String>>(body, "$.data.reviews[*].authorNickname"))
        assertEquals(2, JsonPath.read<Int>(body, "$.data.totalCount"))
        assertEquals(2, JsonPath.read<Int>(body, "$.data.summary.reviewCount"))
        // (5 + 3) / 2 = 4.00
        assertEquals(0, BigDecimal("4.00").compareTo(readBigDecimal(body, "$.data.summary.averageRating")))
        assertEquals(1, JsonPath.read<Int>(body, "$.data.summary.ratingDistribution['5']"))
        assertEquals(1, JsonPath.read<Int>(body, "$.data.summary.ratingDistribution['3']"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.summary.ratingDistribution['1']"))
    }

    @Test
    fun `별점 높은순으로 정렬한다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                val author = persistAuthor("리뷰어")
                persistReview(product.id, author, rating = 2, orderItemId = 1L)
                persistReview(product.id, author, rating = 5, orderItemId = 2L)
                persistReview(product.id, author, rating = 4, orderItemId = 3L)
                product.publicId
            }!!

        val body = getReviews(publicId, sort = "rating_desc")

        assertEquals(listOf(5, 4, 2), JsonPath.read<List<Int>>(body, "$.data.reviews[*].rating"))
    }

    @Test
    fun `포토리뷰만 목록에 반환하고 요약은 전체 가시 리뷰를 기준으로 한다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                val author = persistAuthor("포토리뷰어")
                val withImage = persistReview(product.id, author, rating = 5, orderItemId = 1L, content = "사진 첨부합니다")
                persistReview(product.id, author, rating = 4, orderItemId = 2L, content = "글만 남깁니다")
                em.flush()
                insertReviewImage(withImage.id, "reviews/withimg.jpg", sortOrder = 0)
                product.publicId
            }!!

        val body = getReviews(publicId, photoOnly = true)

        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.reviews").size)
        assertEquals(1, JsonPath.read<Int>(body, "$.data.totalCount"))
        assertEquals(
            "https://fake-cdn.local/reviews/withimg.jpg",
            JsonPath.read<String>(body, "$.data.reviews[0].images[0].imageUrl"),
        )
        // 요약은 photoOnly와 무관하게 전체 가시 리뷰 기준
        assertEquals(2, JsonPath.read<Int>(body, "$.data.summary.reviewCount"))
    }

    @Test
    fun `탈퇴한 작성자는 익명 라벨로 표시되고 프로필 이미지가 없다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                val withdrawn = persistAuthor("원래닉", profileImageKey = "profiles/gone.jpg", withdrawn = true)
                persistReview(product.id, withdrawn, rating = 5, orderItemId = 1L)
                product.publicId
            }!!

        val body = getReviews(publicId)

        assertEquals("탈퇴한 사용자", JsonPath.read<String>(body, "$.data.reviews[0].authorNickname"))
        assertNull(JsonPath.read<String?>(body, "$.data.reviews[0].authorProfileImageUrl"))
    }

    @Test
    fun `소프트 삭제된 리뷰는 목록과 요약에서 제외된다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                val author = persistAuthor("작성자")
                persistReview(product.id, author, rating = 5, orderItemId = 1L, content = "보이는 리뷰")
                val deleted = persistReview(product.id, author, rating = 1, orderItemId = 2L, content = "삭제된 리뷰")
                em.flush()
                softDeleteReview(deleted.id)
                product.publicId
            }!!

        val body = getReviews(publicId)

        assertEquals(listOf("보이는 리뷰"), JsonPath.read<List<String>>(body, "$.data.reviews[*].content"))
        assertEquals(1, JsonPath.read<Int>(body, "$.data.totalCount"))
        assertEquals(1, JsonPath.read<Int>(body, "$.data.summary.reviewCount"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.summary.ratingDistribution['1']"))
    }

    @Test
    fun `MASKED 리뷰는 대체 문구로 노출된다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                val author = persistAuthor("작성자")
                val masked = persistReview(product.id, author, rating = 3, orderItemId = 1L, content = "원문 내용")
                em.flush()
                maskReview(masked.id, displayContent = "노출용 대체 문구")
                product.publicId
            }!!

        val body = getReviews(publicId)

        assertEquals("노출용 대체 문구", JsonPath.read<String>(body, "$.data.reviews[0].content"))
    }

    @Test
    fun `커서로 다음 페이지를 겹침 없이 순회하고 요약과 총개수는 첫 페이지에만 실린다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                val author = persistAuthor("작성자")
                (1..3).forEach { persistReview(product.id, author, rating = 5, orderItemId = it.toLong(), content = "리뷰$it") }
                product.publicId
            }!!

        val page1 = getReviews(publicId, size = 2)
        assertEquals(listOf("리뷰3", "리뷰2"), JsonPath.read<List<String>>(page1, "$.data.reviews[*].content"))
        assertEquals(3, JsonPath.read<Int>(page1, "$.data.totalCount"))
        assertTrue(JsonPath.read<Boolean>(page1, "$.data.hasNext"))

        val page2 = getReviews(publicId, size = 2, cursor = JsonPath.read<String>(page1, "$.data.nextCursor"))
        assertEquals(listOf("리뷰1"), JsonPath.read<List<String>>(page2, "$.data.reviews[*].content"))
        assertFalse(JsonPath.read<Boolean>(page2, "$.data.hasNext"))
        assertNull(JsonPath.read<String?>(page2, "$.data.nextCursor"))
        assertNull(JsonPath.read<Any?>(page2, "$.data.totalCount"))
        assertNull(JsonPath.read<Any?>(page2, "$.data.summary"))
    }

    @Test
    fun `size가 범위를 벗어나거나 커서가 깨졌으면 400을 반환한다`() {
        val publicId = tx.execute { persistVisibleProduct("사료").publicId }!!

        mockMvc
            .perform(get(reviewsPath(publicId)).header(HttpHeaders.AUTHORIZATION, bearer()).param("size", "0"))
            .andExpect(status().isBadRequest)
        mockMvc
            .perform(get(reviewsPath(publicId)).header(HttpHeaders.AUTHORIZATION, bearer()).param("size", "101"))
            .andExpect(status().isBadRequest)
        mockMvc
            .perform(get(reviewsPath(publicId)).header(HttpHeaders.AUTHORIZATION, bearer()).param("cursor", "%%%깨진커서%%%"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `존재하지 않는 상품이면 404를 반환한다`() {
        mockMvc
            .perform(get(reviewsPath("00000000000000000000000000")).header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `인증 토큰이 없으면 401을 반환한다`() {
        val publicId = tx.execute { persistVisibleProduct("사료").publicId }!!

        mockMvc.perform(get(reviewsPath(publicId))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `별점순 커서는 동일 별점이 페이지 경계에 걸려도 겹침 없이 순회한다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                val author = persistAuthor("작성자")
                persistReview(product.id, author, rating = 5, orderItemId = 1L, content = "리뷰1")
                persistReview(product.id, author, rating = 5, orderItemId = 2L, content = "리뷰2")
                persistReview(product.id, author, rating = 5, orderItemId = 3L, content = "리뷰3")
                persistReview(product.id, author, rating = 3, orderItemId = 4L, content = "리뷰4")
                product.publicId
            }!!

        // rating desc, id desc → 리뷰3, 리뷰2, 리뷰1, 리뷰4 (같은 별점 5가 페이지 경계 리뷰2→리뷰1로 이어짐)
        val page1 = getReviews(publicId, sort = "rating_desc", size = 2)
        assertEquals(listOf("리뷰3", "리뷰2"), JsonPath.read<List<String>>(page1, "$.data.reviews[*].content"))

        val page2 = getReviews(publicId, sort = "rating_desc", size = 2, cursor = JsonPath.read<String>(page1, "$.data.nextCursor"))
        assertEquals(listOf("리뷰1", "리뷰4"), JsonPath.read<List<String>>(page2, "$.data.reviews[*].content"))
        assertFalse(JsonPath.read<Boolean>(page2, "$.data.hasNext"))
    }

    @Test
    fun `다른 상품에서 받은 커서는 400으로 거절한다`() {
        val (publicA, publicB) =
            tx.execute {
                val a = persistVisibleProduct("상품A")
                val b = persistVisibleProduct("상품B")
                em.flush()
                val author = persistAuthor("작성자")
                (1..3).forEach { persistReview(a.id, author, rating = 5, orderItemId = it.toLong(), content = "A리뷰$it") }
                (1..3).forEach { persistReview(b.id, author, rating = 5, orderItemId = (10 + it).toLong(), content = "B리뷰$it") }
                a.publicId to b.publicId
            }!!

        val cursorForA = JsonPath.read<String>(getReviews(publicA, size = 1), "$.data.nextCursor")

        mockMvc
            .perform(get(reviewsPath(publicB)).header(HttpHeaders.AUTHORIZATION, bearer()).param("cursor", cursorForA))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `MASKED인데 대체 문구가 없으면 원문 대신 블라인드 문구를 노출한다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                val author = persistAuthor("작성자")
                val masked = persistReview(product.id, author, rating = 2, orderItemId = 1L, content = "민감한 원문")
                em.flush()
                setReviewStatus(masked.id, ReviewStatus.MASKED) // displayContent는 null 유지
                product.publicId
            }!!

        val body = getReviews(publicId)

        assertEquals("블라인드 처리된 리뷰입니다.", JsonPath.read<String>(body, "$.data.reviews[0].content"))
    }

    @Test
    fun `BLOCKED와 HIDDEN 상태 리뷰는 목록과 요약에서 제외된다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct("사료")
                em.flush()
                val author = persistAuthor("작성자")
                persistReview(product.id, author, rating = 5, orderItemId = 1L, content = "공개 리뷰")
                val blocked = persistReview(product.id, author, rating = 1, orderItemId = 2L, content = "차단 리뷰")
                val hidden = persistReview(product.id, author, rating = 1, orderItemId = 3L, content = "숨김 리뷰")
                em.flush()
                setReviewStatus(blocked.id, ReviewStatus.BLOCKED)
                setReviewStatus(hidden.id, ReviewStatus.HIDDEN)
                product.publicId
            }!!

        val body = getReviews(publicId)

        assertEquals(listOf("공개 리뷰"), JsonPath.read<List<String>>(body, "$.data.reviews[*].content"))
        assertEquals(1, JsonPath.read<Int>(body, "$.data.summary.reviewCount"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.summary.ratingDistribution['1']"))
    }

    private fun reviewsPath(publicId: String) = "/api/v1/products/$publicId/reviews"

    private fun bearer(): String {
        val token = cachedToken ?: mintAccessToken(createActiveUser()).also { cachedToken = it }
        return "Bearer $token"
    }

    private fun getReviews(
        publicId: String,
        sort: String? = null,
        photoOnly: Boolean? = null,
        cursor: String? = null,
        size: Int? = null,
    ): String {
        val request = get(reviewsPath(publicId)).header(HttpHeaders.AUTHORIZATION, bearer())
        sort?.let { request.param("sort", it) }
        photoOnly?.let { request.param("photoOnly", it.toString()) }
        cursor?.let { request.param("cursor", it) }
        size?.let { request.param("size", it.toString()) }
        return mockMvc
            .perform(request)
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
    }

    private fun readBigDecimal(
        body: String,
        path: String,
    ): BigDecimal = BigDecimal(JsonPath.read<Any>(body, path).toString())

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

    private fun persistAuthor(
        nickname: String,
        profileImageKey: String? = null,
        withdrawn: Boolean = false,
    ): Long {
        val user = User.preRegister()
        em.persist(user)
        em.flush()
        user.completeOnboarding(nickname)
        em.flush()
        if (profileImageKey != null) {
            em
                .createQuery("update UserProfile p set p.profileImageKey = :key where p.userId = :id")
                .setParameter("key", profileImageKey)
                .setParameter("id", user.id)
                .executeUpdate()
        }
        if (withdrawn) {
            user.withdraw()
            em.flush()
        }
        return user.id
    }

    private fun persistReview(
        productId: Long,
        authorUserId: Long,
        rating: Int,
        orderItemId: Long,
        optionName: String = "블랙 / L",
        content: String = "좋은 상품입니다",
    ): Review {
        val review =
            Review.write(
                productId = productId,
                optionNameSnapshot = optionName,
                orderItemId = orderItemId,
                authorUserId = authorUserId,
                rating = rating,
                content = content,
            )
        em.persist(review)
        return review
    }

    /** 상태 세터가 없어 bulk update로 상태를 바꾼다. */
    private fun softDeleteReview(reviewId: Long) {
        em
            .createQuery("update Review r set r.reviewStatus = :st, r.deletedAt = :now where r.id = :id")
            .setParameter("st", ReviewStatus.DELETED)
            .setParameter("now", LocalDateTime.now())
            .setParameter("id", reviewId)
            .executeUpdate()
    }

    private fun setReviewStatus(
        reviewId: Long,
        status: ReviewStatus,
    ) {
        em
            .createQuery("update Review r set r.reviewStatus = :st where r.id = :id")
            .setParameter("st", status)
            .setParameter("id", reviewId)
            .executeUpdate()
    }

    private fun maskReview(
        reviewId: Long,
        displayContent: String,
    ) {
        em
            .createQuery("update Review r set r.reviewStatus = :st, r.displayContent = :dc where r.id = :id")
            .setParameter("st", ReviewStatus.MASKED)
            .setParameter("dc", displayContent)
            .setParameter("id", reviewId)
            .executeUpdate()
    }

    /** 이미지 추가 경로가 없어 native insert로 넣는다. */
    private fun insertReviewImage(
        reviewId: Long,
        storageKey: String,
        sortOrder: Int,
    ) {
        em
            .createNativeQuery(
                "insert into review_images (review_id, storage_key, sort_order, created_at, updated_at) " +
                    "values (:rid, :key, :ord, now(6), now(6))",
            ).setParameter("rid", reviewId)
            .setParameter("key", storageKey)
            .setParameter("ord", sortOrder)
            .executeUpdate()
    }
}
