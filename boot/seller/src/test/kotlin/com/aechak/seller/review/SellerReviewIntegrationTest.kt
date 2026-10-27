package com.aechak.seller.review

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.review.reply.ReviewReply
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.enums.ReviewStatus
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.user.user.User
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

/**
 * 셀러 리뷰 관리·답글 통합 테스트.
 * 고정하려는 계약: 내 상품 리뷰만 보인다 / 남의 리뷰는 403이 아니라 404 / 답글은 리뷰당 하나 /
 * 별점 분포는 첫 페이지에만 / 셀러 자격 게이트.
 */
class SellerReviewIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String
    private var leafCategoryId = 0L
    private var authorId = 0L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        tx.execute {
            em.persist(Seller.open(sellerUserId, "애착상회", 3000L))
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            leafCategoryId = leaf.id
        }
        authorId = persistAuthor("코코")
    }

    @Test
    fun `내 상품 리뷰만 최신순으로 보이고 남의 상품 리뷰는 목록에 없다`() {
        val mine = persistProduct("자동 급식기 5L", sellerUserId)
        val (otherSellerId, _) = openOtherSeller()
        val theirs = persistProduct("남의 캣타워", otherSellerId)
        val first = persistReview(mine, rating = 5, orderItemId = 1L, content = "밥이 제때 나와요")
        val second = persistReview(mine, rating = 3, orderItemId = 2L, content = "소리가 조금 나요")
        val foreign = persistReview(theirs, rating = 1, orderItemId = 3L, content = "남의 상품 리뷰")

        val body = list()

        assertEquals(listOf(second, first), JsonPath.read<List<Int>>(body, "$.data.reviews[*].reviewId").map { it.toLong() })
        assertEquals(2, JsonPath.read<Int>(body, "$.data.totalCount"))
        assertEquals(
            listOf("자동 급식기 5L", "자동 급식기 5L"),
            JsonPath.read<List<String>>(body, "$.data.reviews[*].productName"),
        )
        assertEquals(emptyList<Long>(), JsonPath.read<List<Int>>(body, "$.data.reviews[?(@.reviewId == $foreign)].reviewId"))
    }

    @Test
    fun `비노출 리뷰는 빠지고 마스킹 리뷰는 공개 본문으로 보인다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val visible = persistReview(productId, rating = 5, orderItemId = 1L, content = "잘 쓰고 있어요")
        val masked = persistReview(productId, rating = 2, orderItemId = 2L, content = "원문 비방 내용")
        val blocked = persistReview(productId, rating = 1, orderItemId = 3L)
        val hidden = persistReview(productId, rating = 1, orderItemId = 4L)
        val deleted = persistReview(productId, rating = 1, orderItemId = 5L)
        tx.execute {
            maskReview(masked, "블라인드 처리된 리뷰입니다.")
            setReviewStatus(blocked, ReviewStatus.BLOCKED)
            setReviewStatus(hidden, ReviewStatus.HIDDEN)
            setReviewStatus(deleted, ReviewStatus.DELETED)
        }

        val body = list()

        assertEquals(
            listOf(masked, visible),
            JsonPath.read<List<Int>>(body, "$.data.reviews[*].reviewId").map { it.toLong() },
        )
        assertEquals(
            "블라인드 처리된 리뷰입니다.",
            JsonPath.read<String>(body, "$.data.reviews[0].content"),
        )
        // 분포도 노출 상태만 센다 — 차단·삭제 리뷰가 1점 막대에 쌓이면 안 된다
        assertEquals(2, JsonPath.read<Int>(body, "$.data.summary.reviewCount"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.summary.ratingDistribution['1']"))
    }

    @Test
    fun `별점 분포와 평균은 첫 페이지에만 실린다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        persistReview(productId, rating = 5, orderItemId = 1L)
        persistReview(productId, rating = 5, orderItemId = 2L)
        persistReview(productId, rating = 2, orderItemId = 3L)

        val firstPage = list("size" to "2")
        assertEquals(3, JsonPath.read<Int>(firstPage, "$.data.summary.reviewCount"))
        assertEquals(0, BigDecimal("4.00").compareTo(readBigDecimal(firstPage, "$.data.summary.averageRating")))
        assertEquals(2, JsonPath.read<Int>(firstPage, "$.data.summary.ratingDistribution['5']"))
        assertEquals(1, JsonPath.read<Int>(firstPage, "$.data.summary.ratingDistribution['2']"))
        assertEquals(0, JsonPath.read<Int>(firstPage, "$.data.summary.ratingDistribution['4']"))
        assertEquals(3, JsonPath.read<Int>(firstPage, "$.data.totalCount"))

        val cursor = JsonPath.read<String>(firstPage, "$.data.nextCursor")
        val secondPage = list("size" to "2", "cursor" to cursor)
        assertNull(JsonPath.read<Any?>(secondPage, "$.data.summary"))
        assertNull(JsonPath.read<Any?>(secondPage, "$.data.totalCount"))
        assertEquals(1, JsonPath.read<List<*>>(secondPage, "$.data.reviews").size)
        assertEquals(false, JsonPath.read<Boolean>(secondPage, "$.data.hasNext"))
    }

    @Test
    fun `리뷰가 없으면 분포는 0으로 채우고 평균은 비운다`() {
        persistProduct("자동 급식기 5L", sellerUserId)

        val body = list()

        assertEquals(0, JsonPath.read<Int>(body, "$.data.summary.reviewCount"))
        assertNull(JsonPath.read<Any?>(body, "$.data.summary.averageRating"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.summary.ratingDistribution['3']"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.totalCount"))
    }

    @Test
    fun `필터를 바꾼 뒤 이전 커서를 다시 쓰면 90002를 반환한다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        persistReview(productId, rating = 5, orderItemId = 1L)
        persistReview(productId, rating = 5, orderItemId = 2L)
        val cursor = JsonPath.read<String>(list("size" to "1"), "$.data.nextCursor")

        request("size" to "1", "cursor" to cursor, "rating" to "5")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `남의 셀러 토큰으로는 같은 커서를 쓸 수 없다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        persistReview(productId, rating = 5, orderItemId = 1L)
        persistReview(productId, rating = 4, orderItemId = 2L)
        val cursor = JsonPath.read<String>(list("size" to "1"), "$.data.nextCursor")
        val (_, otherToken) = openOtherSeller()

        request("size" to "1", "cursor" to cursor, asToken = otherToken)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `상품 필터는 그 상품 리뷰와 분포만 남긴다`() {
        val feeder = persistProduct("자동 급식기 5L", sellerUserId)
        val tower = persistProduct("원목 캣타워 3단", sellerUserId)
        val feederReview = persistReview(feeder, rating = 5, orderItemId = 1L)
        persistReview(tower, rating = 2, orderItemId = 2L)
        val feederPublicId = publicIdOf(feeder)

        val body = list("productId" to feederPublicId)

        assertEquals(listOf(feederReview), JsonPath.read<List<Int>>(body, "$.data.reviews[*].reviewId").map { it.toLong() })
        assertEquals(1, JsonPath.read<Int>(body, "$.data.summary.reviewCount"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.summary.ratingDistribution['2']"))
    }

    @Test
    fun `남의 상품 publicId로 거른 결과는 권한 오류가 아니라 빈 목록이다`() {
        val mine = persistProduct("자동 급식기 5L", sellerUserId)
        persistReview(mine, rating = 5, orderItemId = 1L)
        val (otherSellerId, _) = openOtherSeller()
        val theirs = persistProduct("남의 캣타워", otherSellerId)
        persistReview(theirs, rating = 1, orderItemId = 2L)

        val body = list("productId" to publicIdOf(theirs))

        assertEquals(0, JsonPath.read<List<*>>(body, "$.data.reviews").size)
        assertEquals(0, JsonPath.read<Int>(body, "$.data.summary.reviewCount"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.totalCount"))
    }

    @Test
    fun `별점 필터는 목록과 총건수에 적용되고 분포는 전체를 유지한다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val five = persistReview(productId, rating = 5, orderItemId = 1L)
        persistReview(productId, rating = 2, orderItemId = 2L)

        val body = list("rating" to "5")

        assertEquals(listOf(five), JsonPath.read<List<Int>>(body, "$.data.reviews[*].reviewId").map { it.toLong() })
        assertEquals(1, JsonPath.read<Int>(body, "$.data.totalCount"))
        // 막대는 선택한 상품 범위 전체의 모양이어야 하므로 별점 필터에 흔들리지 않는다
        assertEquals(2, JsonPath.read<Int>(body, "$.data.summary.reviewCount"))
    }

    @Test
    fun `답글 여부 필터가 양쪽으로 동작한다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val answered = persistReview(productId, rating = 5, orderItemId = 1L)
        val unanswered = persistReview(productId, rating = 3, orderItemId = 2L)
        writeReply(answered, "감사합니다").andExpect(status().isCreated)

        val done = list("hasReply" to "true")
        assertEquals(listOf(answered), JsonPath.read<List<Int>>(done, "$.data.reviews[*].reviewId").map { it.toLong() })
        assertEquals(1, JsonPath.read<Int>(done, "$.data.totalCount"))

        val none = list("hasReply" to "false")
        assertEquals(listOf(unanswered), JsonPath.read<List<Int>>(none, "$.data.reviews[*].reviewId").map { it.toLong() })
        assertEquals(1, JsonPath.read<Int>(none, "$.data.totalCount"))
    }

    @Test
    fun `답글을 등록하면 목록에 함께 실린다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val reviewId = persistReview(productId, rating = 2, orderItemId = 1L)

        writeReply(reviewId, "  사이즈표를 실측값으로 고쳤어요.  ")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.reviewId").value(reviewId))
            .andExpect(jsonPath("$.data.content").value("사이즈표를 실측값으로 고쳤어요."))
            .andExpect(jsonPath("$.data.replyId").isNumber)

        val body = list()
        assertEquals("사이즈표를 실측값으로 고쳤어요.", JsonPath.read<String>(body, "$.data.reviews[0].reply.content"))
        assertNotNull(JsonPath.read<Any>(body, "$.data.reviews[0].reply.repliedAt"))
    }

    @Test
    fun `이미 답글이 있는 리뷰에 또 등록하면 109201로 거절하고 원래 답글이 남는다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val reviewId = persistReview(productId, rating = 2, orderItemId = 1L)
        writeReply(reviewId, "첫 답글").andExpect(status().isCreated)

        writeReply(reviewId, "두 번째 답글")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(110201))

        assertEquals("첫 답글", replyContentOf(reviewId))
        assertEquals(1, replyRowCount(reviewId))
    }

    @Test
    fun `답글을 수정하면 내용이 바뀌고 행은 늘지 않는다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val reviewId = persistReview(productId, rating = 2, orderItemId = 1L)
        writeReply(reviewId, "첫 답글").andExpect(status().isCreated)

        editReply(reviewId, "고친 답글")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content").value("고친 답글"))

        assertEquals("고친 답글", replyContentOf(reviewId))
        assertEquals(1, replyRowCount(reviewId))
    }

    @Test
    fun `답글을 지우면 행이 사라지고 목록의 답글도 비워진다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val reviewId = persistReview(productId, rating = 2, orderItemId = 1L)
        writeReply(reviewId, "첫 답글").andExpect(status().isCreated)

        deleteReply(reviewId).andExpect(status().isNoContent)

        assertEquals(0, replyRowCount(reviewId))
        assertNull(JsonPath.read<Any?>(list(), "$.data.reviews[0].reply"))
        // 지운 뒤에는 다시 등록할 수 있다
        writeReply(reviewId, "다시 쓴 답글").andExpect(status().isCreated)
    }

    @Test
    fun `답글이 없는 리뷰를 수정·삭제하면 110200을 반환한다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val reviewId = persistReview(productId, rating = 2, orderItemId = 1L)

        editReply(reviewId, "없는 답글 수정")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(110200))
        deleteReply(reviewId)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(110200))
    }

    @Test
    fun `남의 상품 리뷰는 없는 것으로 보여 답글을 달 수 없다`() {
        val (otherSellerId, _) = openOtherSeller()
        val theirs = persistProduct("남의 캣타워", otherSellerId)
        val foreignReview = persistReview(theirs, rating = 1, orderItemId = 1L)

        // 403이 아니라 404 — 셀러에게 남의 리뷰가 있다는 사실조차 알리지 않는다
        writeReply(foreignReview, "남의 리뷰에 답글")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(110000))
        assertEquals(0, replyRowCount(foreignReview))

        editReply(foreignReview, "남의 리뷰 수정").andExpect(jsonPath("$.errorCode").value(110000))
        deleteReply(foreignReview).andExpect(jsonPath("$.errorCode").value(110000))
    }

    @Test
    fun `남의 리뷰에 이미 답글이 있어도 남의 셀러는 건드릴 수 없다`() {
        val (otherSellerId, otherToken) = openOtherSeller()
        val theirs = persistProduct("남의 캣타워", otherSellerId)
        val foreignReview = persistReview(theirs, rating = 1, orderItemId = 1L)
        writeReply(foreignReview, "원래 셀러의 답글", asToken = otherToken).andExpect(status().isCreated)

        editReply(foreignReview, "가로챈 답글")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(110000))
        deleteReply(foreignReview).andExpect(status().isNotFound)

        assertEquals("원래 셀러의 답글", replyContentOf(foreignReview))
    }

    @Test
    fun `비노출 리뷰에는 답글을 달 수 없다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val blocked = persistReview(productId, rating = 1, orderItemId = 1L)
        tx.execute { setReviewStatus(blocked, ReviewStatus.BLOCKED) }

        writeReply(blocked, "차단된 리뷰에 답글")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(110000))
        assertEquals(0, replyRowCount(blocked))
    }

    @Test
    fun `없는 리뷰에 답글을 달면 110000을 반환한다`() {
        persistProduct("자동 급식기 5L", sellerUserId)

        writeReply(999_999L, "없는 리뷰")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(110000))
    }

    @Test
    fun `답글 길이 경계를 지키고 빈 내용은 거절한다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val boundary = persistReview(productId, rating = 5, orderItemId = 1L)
        val overflow = persistReview(productId, rating = 5, orderItemId = 2L)
        val blank = persistReview(productId, rating = 5, orderItemId = 3L)

        writeReply(boundary, "가".repeat(ReviewReply.CONTENT_MAX)).andExpect(status().isCreated)

        writeReply(overflow, "가".repeat(ReviewReply.CONTENT_MAX + 1))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
        assertEquals(0, replyRowCount(overflow))

        writeReply(blank, "   ")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
        assertEquals(0, replyRowCount(blank))
    }

    @Test
    fun `범위 밖 별점·size는 90001을 반환한다`() {
        persistProduct("자동 급식기 5L", sellerUserId)

        request("rating" to "0").andExpect(status().isBadRequest).andExpect(jsonPath("$.errorCode").value(90001))
        request("rating" to "6").andExpect(status().isBadRequest).andExpect(jsonPath("$.errorCode").value(90001))
        request("size" to "0").andExpect(status().isBadRequest).andExpect(jsonPath("$.errorCode").value(90001))
        request("size" to "101").andExpect(status().isBadRequest).andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `형식이 깨진 상품 식별자는 90001을 반환한다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        persistReview(productId, rating = 5, orderItemId = 1L)
        persistReview(productId, rating = 5, orderItemId = 2L)

        // 커서가 이 값을 담아 필터 변경을 판정하므로 커서 구분자(:)가 섞이면 커서를 만드는 자리에서
        // 터진다 — 다음 페이지가 있는 상황(size=1)으로 그 경로를 지나게 해 500이 아닌지 본다
        mockMvc
            .perform(
                get(LIST_URL)
                    .param("productId", "01JABC:DEF")
                    .param("size", "1")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
            .andExpect(jsonPath("$.message").value("productId: 상품 식별자 형식이 올바르지 않습니다."))

        request("productId" to "짧다", "size" to "1")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))

        // 형식이 맞는 값은 남의 것이어도 형식 오류가 아니다 — 빈 목록으로 응답한다
        request("productId" to "0".repeat(26)).andExpect(status().isOk)
    }

    @Test
    fun `인증 없이는 접근할 수 없다`() {
        mockMvc.perform(get(LIST_URL)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `정지·탈퇴 셀러와 비셀러는 110011을 반환한다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val reviewId = persistReview(productId, rating = 5, orderItemId = 1L)

        tx.execute { setSellerStatus(sellerUserId, SellerStatus.SUSPENDED) }
        request().andExpect(status().isForbidden).andExpect(jsonPath("$.errorCode").value(110011))
        writeReply(reviewId, "정지 중 답글").andExpect(status().isForbidden).andExpect(jsonPath("$.errorCode").value(110011))
        assertEquals(0, replyRowCount(reviewId))

        tx.execute { setSellerStatus(sellerUserId, SellerStatus.WITHDRAWN) }
        request().andExpect(status().isForbidden).andExpect(jsonPath("$.errorCode").value(110011))

        val notSeller = createActiveUser()
        request(asToken = mintAccessToken(notSeller))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(110011))
    }

    @Test
    fun `휴점 셀러는 조회와 답글이 모두 되고 탈퇴신청 셀러는 조회만 된다`() {
        val productId = persistProduct("자동 급식기 5L", sellerUserId)
        val pausedReview = persistReview(productId, rating = 5, orderItemId = 1L)
        val requestedReview = persistReview(productId, rating = 4, orderItemId = 2L)

        tx.execute { setSellerStatus(sellerUserId, SellerStatus.PAUSED) }
        request().andExpect(status().isOk)
        writeReply(pausedReview, "휴점 중에도 응대합니다").andExpect(status().isCreated)

        tx.execute { setSellerStatus(sellerUserId, SellerStatus.WITHDRAWAL_REQUESTED) }
        request().andExpect(status().isOk)
        writeReply(requestedReview, "탈퇴신청 중 답글")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(110011))
        assertEquals(0, replyRowCount(requestedReview))
    }

    private fun list(vararg params: Pair<String, String>): String =
        request(*params)
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun request(
        vararg params: Pair<String, String>,
        asToken: String = token,
    ): ResultActions =
        mockMvc.perform(
            params
                .fold(get(LIST_URL)) { req, (key, value) -> req.param(key, value) }
                .header(HttpHeaders.AUTHORIZATION, "Bearer $asToken"),
        )

    private fun writeReply(
        reviewId: Long,
        content: String,
        asToken: String = token,
    ): ResultActions =
        mockMvc.perform(
            post("$LIST_URL/$reviewId/reply")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $asToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(replyBody(content)),
        )

    private fun editReply(
        reviewId: Long,
        content: String,
        asToken: String = token,
    ): ResultActions =
        mockMvc.perform(
            put("$LIST_URL/$reviewId/reply")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $asToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(replyBody(content)),
        )

    private fun deleteReply(
        reviewId: Long,
        asToken: String = token,
    ): ResultActions =
        mockMvc.perform(
            delete("$LIST_URL/$reviewId/reply").header(HttpHeaders.AUTHORIZATION, "Bearer $asToken"),
        )

    private fun replyBody(content: String): String = """{"content": ${quote(content)}}"""

    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun replyRowCount(reviewId: Long): Long =
        tx.execute {
            em
                .createQuery("select count(r) from ReviewReply r where r.reviewId = :id", java.lang.Long::class.java)
                .setParameter("id", reviewId)
                .singleResult
                .toLong()
        }!!

    private fun replyContentOf(reviewId: Long): String? =
        tx.execute {
            em
                .createQuery("select r.content from ReviewReply r where r.reviewId = :id", String::class.java)
                .setParameter("id", reviewId)
                .resultList
                .firstOrNull()
        }

    private fun readBigDecimal(
        body: String,
        path: String,
    ): BigDecimal = BigDecimal(JsonPath.read<Any>(body, path).toString())

    private fun openOtherSeller(): Pair<Long, String> {
        val otherUserId = createActiveUser()
        tx.execute { em.persist(Seller.open(otherUserId, "옆집상회", 3000L)) }
        return otherUserId to mintAccessToken(otherUserId)
    }

    private fun persistProduct(
        name: String,
        ownerId: Long,
    ): Long =
        tx.execute {
            val product =
                Product.register(
                    category = em.find(Category::class.java, leafCategoryId),
                    sellerId = ownerId,
                    name = name,
                    description = null,
                    representativeImageKey = null,
                    regularPrice = 25000L,
                    discountPrice = null,
                    discountStartAt = null,
                    discountEndAt = null,
                )
            em.persist(product)
            em.flush()
            product.id
        }!!

    private fun publicIdOf(productId: Long): String = tx.execute { em.find(Product::class.java, productId).publicId }!!

    private fun persistReview(
        productId: Long,
        rating: Int,
        orderItemId: Long,
        content: String = "좋은 상품입니다",
    ): Long =
        tx.execute {
            val review =
                Review.write(
                    productId = productId,
                    optionNameSnapshot = "기본",
                    orderItemId = orderItemId,
                    authorUserId = authorId,
                    rating = rating,
                    content = content,
                )
            em.persist(review)
            em.flush()
            review.id
        }!!

    private fun persistAuthor(nickname: String): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            em.flush()
            user.completeOnboarding(nickname)
            em.flush()
            user.id
        }!!

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

    private fun setSellerStatus(
        userId: Long,
        status: SellerStatus,
    ) {
        em
            .createQuery("update Seller s set s.status = :st where s.userId = :id")
            .setParameter("st", status)
            .setParameter("id", userId)
            .executeUpdate()
    }

    private companion object {
        const val LIST_URL = "/api/v1/sellers/me/reviews"
    }
}
