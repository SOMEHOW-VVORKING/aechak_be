package com.aechak.api.report

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.product.report.usecase.ProductReportUseCase
import com.aechak.application.product.report.usecase.command.SubmitProductReportCommand
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.report.ProductReport
import com.aechak.domain.product.report.enums.ProductReportReason
import com.aechak.domain.product.report.enums.ProductReportStatus
import com.aechak.domain.product.report.event.ProductReportReceivedEvent
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.report.ReviewReport
import com.aechak.domain.review.report.enums.ReviewReportReason
import com.aechak.domain.review.report.enums.ReviewReportStatus
import com.aechak.domain.review.report.event.ReviewReportReceivedEvent
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.enums.ReviewStatus
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.support.Ulid
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.report.UserReport
import com.aechak.domain.user.report.enums.ReportReasonCode
import com.aechak.domain.user.report.enums.ReportStatus
import com.aechak.domain.user.report.event.UserReportReceivedEvent
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 통합 테스트. POST /reports가 대상 종류별로 신고를 저장하고 막아야 할 신고를 각 BC의 코드로 돌려주며 접수 이벤트를 한 번만 발행하는지 HTTP부터 고정한다.
 * 깨지면 본인 신고나 중복 신고, 보이지 않는 대상 신고가 저장되거나 다른 BC의 코드가 응답에 섞인다.
 */
@RecordApplicationEvents
class ReportIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    @Autowired
    private lateinit var productReportUseCase: ProductReportUseCase

    private lateinit var mockMvc: MockMvc
    private var reporterId = 0L
    private lateinit var reporterToken: String
    private var nextOrderItemId = 1L
    private val sellerUserId = 77L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        reporterId = createActiveUser()
        reporterToken = mintAccessToken(reporterId)
        // 다른 작성자의 공개 리뷰 두 개를 먼저 저장해 테스트 리뷰 id를 3부터 시작한다
        repeat(2) { persistReview(authorUserId = 9_999L) }
    }

    // ---------- 공통 ----------

    @Test
    fun `대상 종류가 없거나 알 수 없으면 400과 INVALID_REQUEST를 반환한다`() {
        val publicId = persistVisibleProduct(sellerUserId)

        listOf(reportJson(null, publicId, "FALSE_AD"), reportJson("SELLER", publicId, "FALSE_AD")).forEach { body ->
            postReport(reporterToken, body)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
        }
        assertNothingReported()
    }

    @Test
    fun `토큰 없이 신고하면 401과 UNAUTHENTICATED를 반환한다`() {
        val publicId = persistVisibleProduct(sellerUserId)

        postReport(null, reportJson("PRODUCT", publicId, "FALSE_AD"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.UNAUTHENTICATED.code))
        assertNothingReported()
    }

    @Test
    fun `온보딩을 마치지 않은 사용자가 신고하면 403과 ONBOARDING_REQUIRED를 반환한다`() {
        val publicId = persistVisibleProduct(sellerUserId)

        postReport(mintAccessToken(createPendingUser()), reportJson("PRODUCT", publicId, "FALSE_AD"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ONBOARDING_REQUIRED.code))
        assertNothingReported()
    }

    @Test
    fun `같은 대상도 신고자가 다르면 대상 종류와 상관없이 각각 접수한다`() {
        val otherReporterToken = mintAccessToken(createActiveUser())
        val publicId = persistVisibleProduct(sellerUserId)
        val authorId = createActiveUser()
        val reviewId = persistReview(authorId)

        listOf(reporterToken, otherReporterToken).forEach { token ->
            postReport(token, reportJson("PRODUCT", publicId, "FALSE_AD")).andExpect(status().isCreated)
            postReport(token, reportJson("REVIEW", reviewId.toString(), "SPAM")).andExpect(status().isCreated)
            postReport(token, reportJson("USER", reviewId.toString(), "SPAM")).andExpect(status().isCreated)
        }

        assertEquals(listOf(publicId, publicId), productReports().map { it.product.publicId }, "상품 신고는 신고자마다 하나씩 남아야 한다")
        assertEquals(listOf(reviewId, reviewId), reviewReports().map { it.review.id }, "리뷰 신고는 신고자마다 하나씩 남아야 한다")
        assertEquals(listOf(authorId, authorId), userReports().map { it.targetUser.id }, "사용자 신고는 신고자마다 하나씩 남아야 한다")
        assertEquals(2, recorded<ProductReportReceivedEvent>().size, "상품 신고 접수 이벤트는 신고마다 발행해야 한다")
        assertEquals(2, recorded<ReviewReportReceivedEvent>().size, "리뷰 신고 접수 이벤트는 신고마다 발행해야 한다")
        assertEquals(2, recorded<UserReportReceivedEvent>().size, "사용자 신고 접수 이벤트는 신고마다 발행해야 한다")
    }

    @Test
    fun `같은 신고자도 대상이 다르면 대상 종류와 상관없이 각각 접수한다`() {
        val publicIds = List(2) { persistVisibleProduct(sellerUserId) }
        val reviewIds = List(2) { persistReview(createActiveUser()) }

        publicIds.forEach { publicId ->
            postReport(reporterToken, reportJson("PRODUCT", publicId, "FALSE_AD")).andExpect(status().isCreated)
        }
        reviewIds.forEach { reviewId ->
            postReport(reporterToken, reportJson("REVIEW", reviewId.toString(), "SPAM")).andExpect(status().isCreated)
            postReport(reporterToken, reportJson("USER", reviewId.toString(), "SPAM")).andExpect(status().isCreated)
        }

        assertEquals(listOf(reporterId, reporterId), productReports().map { it.reporterId }, "상품 신고는 상품마다 하나씩 남아야 한다")
        assertEquals(listOf(reporterId, reporterId), reviewReports().map { it.reporterId }, "리뷰 신고는 리뷰마다 하나씩 남아야 한다")
        assertEquals(listOf(reporterId, reporterId), userReports().map { it.reporter.id }, "사용자 신고는 작성자마다 하나씩 남아야 한다")
    }

    // ---------- 상품 ----------

    @Test
    fun `상품을 신고하면 접수 상태로 저장하고 접수 이벤트를 한 번 발행하며 본문 없이 201을 반환한다`() {
        val publicId = persistVisibleProduct(sellerUserId)

        postReport(reporterToken, reportJson("PRODUCT", publicId, "FALSE_AD", "   "))
            .andExpect(status().isCreated)
            .andExpect(content().string(""))

        val reports = productReports()
        assertEquals(1, reports.size, "상품 신고 하나를 저장해야 한다")
        val saved = reports[0]
        assertEquals(reporterId, saved.reporterId, "신고자 id를 저장해야 한다")
        assertEquals(publicId, saved.product.publicId, "신고 대상 상품을 저장해야 한다")
        assertEquals(ProductReportReason.FALSE_AD, saved.reasonCode, "사유 코드를 저장해야 한다")
        assertNull(saved.reasonText, "공백뿐인 상세 사유는 null로 저장해야 한다")
        assertEquals(ProductReportStatus.RECEIVED, saved.status, "접수 상태로 저장해야 한다")
        val events = recorded<ProductReportReceivedEvent>()
        assertEquals(1, events.size, "접수 이벤트는 한 번만 발행해야 한다")
        assertEquals(saved.id, events[0].reportId, "이벤트에 저장한 신고 id를 담아야 한다")
        assertEquals(publicId, events[0].productPublicId, "이벤트에 상품 publicId를 담아야 한다")
    }

    @Test
    fun `상품 신고가 기타 사유인데 상세 사유가 공백뿐이면 400과 40300을 반환한다`() {
        val publicId = persistVisibleProduct(sellerUserId)

        postReport(reporterToken, reportJson("PRODUCT", publicId, "OTHER", "   "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(ProductErrorCode.PRODUCT_REPORT_REASON_TEXT_REQUIRED.code))
        assertNothingReported()
    }

    @Test
    fun `본인 상품을 신고하면 400과 40302를 반환한다`() {
        val publicId = persistVisibleProduct(sellerUserId = reporterId)

        postReport(reporterToken, reportJson("PRODUCT", publicId, "FALSE_AD"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(ProductErrorCode.PRODUCT_SELF_REPORT_NOT_ALLOWED.code))
        assertNothingReported()
    }

    @Test
    fun `판매 중지됐거나 없는 상품을 신고하면 404와 40000을 반환한다`() {
        val suspended = persistVisibleProduct(sellerUserId)
        changeSaleStatus(suspended, SaleStatus.SUSPENDED)

        listOf(suspended, Ulid.generate()).forEach { targetId ->
            postReport(reporterToken, reportJson("PRODUCT", targetId, "FALSE_AD"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(ProductErrorCode.PRODUCT_NOT_FOUND.code))
        }
        assertNothingReported()
    }

    @Test
    fun `같은 상품을 다시 신고하면 409와 40303을 반환하고 신고와 이벤트는 하나만 남는다`() {
        val body = reportJson("PRODUCT", persistVisibleProduct(sellerUserId), "FALSE_AD")

        postReport(reporterToken, body).andExpect(status().isCreated)
        postReport(reporterToken, body)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(ProductErrorCode.PRODUCT_ALREADY_REPORTED.code))

        assertEquals(1, productReports().size, "신고는 하나만 남아야 한다")
        assertEquals(1, recorded<ProductReportReceivedEvent>().size, "접수 이벤트는 처음 한 번만 발행해야 한다")
    }

    @Test
    fun `대상에 맞지 않는 사유 코드면 400과 INVALID_REQUEST를 반환한다`() {
        val publicId = persistVisibleProduct(sellerUserId)

        postReport(reporterToken, reportJson("PRODUCT", publicId, "FRAUD"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
        assertNothingReported()
    }

    @Test
    fun `상세 사유는 500자까지 받고 넘으면 400과 INVALID_REQUEST를 반환한다`() {
        val publicId = persistVisibleProduct(sellerUserId)

        postReport(reporterToken, reportJson("PRODUCT", publicId, "OTHER", "가".repeat(501)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
        postReport(reporterToken, reportJson("PRODUCT", publicId, "OTHER", "가".repeat(500)))
            .andExpect(status().isCreated)

        val reports = productReports()
        assertEquals(1, reports.size, "500자 신고 하나만 저장해야 한다")
        assertEquals(500, reports[0].reasonText?.length, "500자 상세 사유를 그대로 저장해야 한다")
    }

    @Test
    fun `같은 상품을 동시에 여러 번 신고해도 한 건만 저장되고 나머지는 40303으로 거절된다`() {
        val publicId = persistVisibleProduct(sellerUserId)
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
                            productReportUseCase.submitProductReport(
                                SubmitProductReportCommand(reporterId, publicId, ProductReportReason.FALSE_AD, null),
                            )
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

        assertEquals(1, productReports().size, "UNIQUE가 한 건만 남겨야 한다")
        assertEquals(threads - 1, errors.size, "나머지 요청은 모두 거절돼야 한다: $errors")
        assertTrue(
            errors.all { it is BusinessException && it.errorCode == ProductErrorCode.PRODUCT_ALREADY_REPORTED },
            "거절은 모두 중복 신고 코드여야 한다: $errors",
        )
        assertEquals(1, recorded<ProductReportReceivedEvent>().size, "접수 이벤트는 성공한 한 건만 발행돼야 한다")
    }

    // ---------- 리뷰 ----------

    @Test
    fun `리뷰를 신고하면 처리 대기 상태로 저장하고 상세 사유의 앞뒤 공백을 지운다`() {
        val reviewId = persistReview(authorUserId = createActiveUser())
        assertNotEquals(reporterId, reviewId, "전제: 신고자 id와 리뷰 id가 같으면 두 값이 바뀌어도 알 수 없다")

        postReport(reporterToken, reportJson("REVIEW", reviewId.toString(), "FRAUD", "  허위 리뷰  "))
            .andExpect(status().isCreated)

        val reports = reviewReports()
        assertEquals(1, reports.size, "리뷰 신고 하나를 저장해야 한다")
        val saved = reports[0]
        assertNotEquals(saved.id, reviewId, "전제: 신고 id와 리뷰 id가 같으면 이벤트에서 두 값이 바뀌어도 알 수 없다")
        assertEquals(reviewId, saved.review.id, "신고 대상 리뷰를 저장해야 한다")
        assertEquals(reporterId, saved.reporterId, "신고자 id를 저장해야 한다")
        assertEquals(ReviewReportReason.FRAUD, saved.reasonCode, "사유 코드를 저장해야 한다")
        assertEquals("허위 리뷰", saved.reasonText, "상세 사유의 앞뒤 공백을 지워 저장해야 한다")
        assertEquals(ReviewReportStatus.PENDING, saved.status, "처리 대기 상태로 저장해야 한다")
        val events = recorded<ReviewReportReceivedEvent>()
        assertEquals(1, events.size, "접수 이벤트는 한 번만 발행해야 한다")
        assertEquals(saved.id, events[0].reportId, "이벤트에 저장한 신고 id를 담아야 한다")
        assertEquals(reviewId, events[0].reviewId, "이벤트에 리뷰 id를 담아야 한다")
    }

    @Test
    fun `본인 리뷰를 신고하면 400과 110101을 반환한다`() {
        val reviewId = persistReview(authorUserId = reporterId)

        postReport(reporterToken, reportJson("REVIEW", reviewId.toString(), "SPAM"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(ReviewErrorCode.REVIEW_SELF_REPORT_NOT_ALLOWED.code))
        assertNothingReported()
    }

    @Test
    fun `리뷰 신고도 기타 사유면 상세 사유가 있어야 해서 400과 110102를 반환한다`() {
        val reviewId = persistReview(authorUserId = createActiveUser())

        postReport(reporterToken, reportJson("REVIEW", reviewId.toString(), "OTHER"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(ReviewErrorCode.REVIEW_REPORT_REASON_TEXT_REQUIRED.code))
        assertNothingReported()
    }

    @Test
    fun `공개 중이 아닌 리뷰를 신고하면 404와 110000을 반환한다`() {
        val authorId = createActiveUser()
        val blocked = persistReview(authorId).also { changeReviewStatus(it, ReviewStatus.BLOCKED) }
        val deleted = persistReview(authorId).also { changeReviewStatus(it, ReviewStatus.DELETED) }

        listOf(blocked, deleted).forEach { reviewId ->
            postReport(reporterToken, reportJson("REVIEW", reviewId.toString(), "SPAM"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(ReviewErrorCode.REVIEW_NOT_FOUND.code))
        }
        assertNothingReported()
    }

    @Test
    fun `같은 리뷰를 다시 신고하면 409와 110103을 반환한다`() {
        val body = reportJson("REVIEW", persistReview(createActiveUser()).toString(), "SPAM")

        postReport(reporterToken, body).andExpect(status().isCreated)
        postReport(reporterToken, body)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(ReviewErrorCode.REVIEW_ALREADY_REPORTED.code))

        assertEquals(1, reviewReports().size, "신고는 하나만 남아야 한다")
        assertEquals(1, recorded<ReviewReportReceivedEvent>().size, "접수 이벤트는 처음 한 번만 발행해야 한다")
    }

    @Test
    fun `리뷰 id가 양의 정수가 아니면 400과 INVALID_REQUEST를 반환한다`() {
        listOf("abc", "0").forEach { targetId ->
            postReport(reporterToken, reportJson("REVIEW", targetId, "SPAM"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
        }
        assertNothingReported()
    }

    // ---------- 사용자 ----------

    @Test
    fun `리뷰 작성자를 신고하면 그 사용자에 대한 신고로 저장하고 공백뿐인 상세 사유는 비운다`() {
        val authorId = createActiveUser()
        val reviewId = persistReview(authorId)
        assertNotEquals(reporterId, reviewId, "전제: 신고자 id와 리뷰 id가 같으면 두 값이 바뀌어도 알 수 없다")

        postReport(reporterToken, reportJson("USER", reviewId.toString(), "ABUSE", "   "))
            .andExpect(status().isCreated)

        val reports = userReports()
        assertEquals(1, reports.size, "사용자 신고 하나를 저장해야 한다")
        val saved = reports[0]
        assertEquals(
            3,
            setOf(saved.id, authorId, reviewId).size,
            "전제: 신고 id, 대상 사용자 id, 리뷰 id 가운데 둘이 같으면 이벤트에서 두 값이 바뀌어도 알 수 없다",
        )
        assertEquals(authorId, saved.targetUser.id, "리뷰 작성자를 신고 대상으로 저장해야 한다")
        assertEquals(reporterId, saved.reporter.id, "신고자를 저장해야 한다")
        assertEquals(ReportReasonCode.ABUSE, saved.reasonCode, "사유 코드를 저장해야 한다")
        assertNull(saved.reasonText, "공백뿐인 상세 사유는 null로 저장해야 한다")
        assertEquals(ReportStatus.RECEIVED, saved.status, "접수 상태로 저장해야 한다")
        assertTrue(reviewReports().isEmpty(), "사용자 신고는 리뷰 신고를 남기지 않아야 한다")
        val events = recorded<UserReportReceivedEvent>()
        assertEquals(1, events.size, "접수 이벤트는 한 번만 발행해야 한다")
        assertEquals(saved.id, events[0].reportId, "이벤트에 저장한 신고 id를 담아야 한다")
        assertEquals(authorId, events[0].targetUserId, "이벤트에 신고 대상 사용자 id를 담아야 한다")
        assertEquals(reviewId, events[0].reviewId, "이벤트에 신고 대상을 고른 리뷰 id를 담아야 한다")
    }

    @Test
    fun `자기 리뷰로 자신을 신고하면 400과 30200을 반환한다`() {
        val reviewId = persistReview(authorUserId = reporterId)

        postReport(reporterToken, reportJson("USER", reviewId.toString(), "SPAM"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.SELF_REPORT_NOT_ALLOWED.code))
        assertNothingReported()
    }

    @Test
    fun `사용자 신고도 기타 사유면 상세 사유가 있어야 해서 400과 30201을 반환한다`() {
        val reviewId = persistReview(authorUserId = createActiveUser())

        postReport(reporterToken, reportJson("USER", reviewId.toString(), "OTHER", "  "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.REPORT_REASON_REQUIRED.code))
        assertNothingReported()
    }

    @Test
    fun `사용자 신고도 기타 사유에 상세 사유가 있으면 앞뒤 공백을 지워 저장한다`() {
        val reviewId = persistReview(authorUserId = createActiveUser())

        postReport(reporterToken, reportJson("USER", reviewId.toString(), "OTHER", "  욕설  "))
            .andExpect(status().isCreated)

        assertEquals("욕설", userReports().single().reasonText, "상세 사유의 앞뒤 공백을 지워 저장해야 한다")
    }

    @Test
    fun `공개 중이 아니거나 없는 리뷰로는 사용자를 신고할 수 없어 404와 30000을 반환한다`() {
        val blocked = persistReview(createActiveUser()).also { changeReviewStatus(it, ReviewStatus.BLOCKED) }

        listOf(blocked, Long.MAX_VALUE).forEach { reviewId ->
            postReport(reporterToken, reportJson("USER", reviewId.toString(), "SPAM"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(UserErrorCode.USER_NOT_FOUND.code))
        }
        assertNothingReported()
    }

    @Test
    fun `탈퇴한 작성자는 공개 중인 리뷰로도 신고할 수 없어 404와 30000을 반환한다`() {
        val authorId = createActiveUser()
        val reviewId = persistReview(authorId)
        withdraw(authorId)

        postReport(reporterToken, reportJson("USER", reviewId.toString(), "SPAM"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.USER_NOT_FOUND.code))
        assertNothingReported()
    }

    @Test
    fun `같은 작성자를 다른 리뷰로 다시 신고해도 409와 30202를 반환한다`() {
        val authorId = createActiveUser()
        val firstReviewId = persistReview(authorId)
        val secondReviewId = persistReview(authorId)

        postReport(reporterToken, reportJson("USER", firstReviewId.toString(), "SPAM"))
            .andExpect(status().isCreated)
        postReport(reporterToken, reportJson("USER", secondReviewId.toString(), "SPAM"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.USER_ALREADY_REPORTED.code))

        assertEquals(1, userReports().size, "중복 기준은 리뷰가 아니라 사용자라서 신고는 하나만 남아야 한다")
        assertEquals(1, recorded<UserReportReceivedEvent>().size, "접수 이벤트는 처음 한 번만 발행해야 한다")
    }

    @Test
    fun `신고한 작성자가 탈퇴하면 같은 리뷰나 그 작성자의 다른 리뷰로 다시 신고해도 404와 30000을 반환하고 신고와 이벤트는 하나만 남는다`() {
        val authorId = createActiveUser()
        val firstReviewId = persistReview(authorId)
        val secondReviewId = persistReview(authorId)

        postReport(reporterToken, reportJson("USER", firstReviewId.toString(), "SPAM"))
            .andExpect(status().isCreated)
        withdraw(authorId)

        listOf(firstReviewId, secondReviewId).forEach { reviewId ->
            postReport(reporterToken, reportJson("USER", reviewId.toString(), "SPAM"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(UserErrorCode.USER_NOT_FOUND.code))
        }

        assertEquals(1, userReports().size, "탈퇴 전에 접수한 신고 하나만 남아야 한다")
        assertEquals(1, recorded<UserReportReceivedEvent>().size, "접수 이벤트는 탈퇴 전 신고에서 한 번만 발행해야 한다")
    }

    @Test
    fun `가려진(MASKED) 리뷰와 그 작성자도 신고할 수 있다`() {
        val reviewId = persistReview(createActiveUser()).also { changeReviewStatus(it, ReviewStatus.MASKED) }

        postReport(reporterToken, reportJson("REVIEW", reviewId.toString(), "SPAM"))
            .andExpect(status().isCreated)
        postReport(reporterToken, reportJson("USER", reviewId.toString(), "SPAM"))
            .andExpect(status().isCreated)

        assertEquals(1, reviewReports().size, "가려진 리뷰의 신고를 저장해야 한다")
        assertEquals(1, userReports().size, "가려진 리뷰 작성자의 신고를 저장해야 한다")
    }

    @Test
    fun `리뷰와 사용자 신고도 상세 사유는 500자까지 받고 넘으면 400과 INVALID_REQUEST를 반환한다`() {
        val reviewId = persistReview(authorUserId = createActiveUser()).toString()

        listOf("REVIEW", "USER").forEach { targetType ->
            postReport(reporterToken, reportJson(targetType, reviewId, "OTHER", "가".repeat(501)))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
        }
        assertNothingReported()

        listOf("REVIEW", "USER").forEach { targetType ->
            postReport(reporterToken, reportJson(targetType, reviewId, "OTHER", "가".repeat(500)))
                .andExpect(status().isCreated)
        }
        assertEquals(500, reviewReports().single().reasonText?.length, "리뷰 신고의 500자 상세 사유를 그대로 저장해야 한다")
        assertEquals(500, userReports().single().reasonText?.length, "사용자 신고의 500자 상세 사유를 그대로 저장해야 한다")
    }

    // ---------- 헬퍼 ----------

    private fun postReport(
        token: String?,
        body: String,
    ): ResultActions {
        val request = post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON).content(body)
        token?.let { request.header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        return mockMvc.perform(request)
    }

    private fun reportJson(
        targetType: String?,
        targetId: String,
        reasonCode: String,
        reasonText: String? = null,
    ): String =
        listOfNotNull(
            targetType?.let { "\"targetType\":\"$it\"" },
            "\"targetId\":\"$targetId\"",
            "\"reasonCode\":\"$reasonCode\"",
            reasonText?.let { "\"reasonText\":\"$it\"" },
        ).joinToString(separator = ",", prefix = "{", postfix = "}")

    /** 구매자에게 노출되는 상품을 심고 publicId를 돌려준다 */
    private fun persistVisibleProduct(sellerUserId: Long): String =
        tx.execute {
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "산책용품", null, 1)
            em.persist(mid)
            if (em.find(Seller::class.java, sellerUserId) == null) {
                em.persist(Seller.open(userId = sellerUserId, storeName = "store-$sellerUserId", baseShippingFee = 3000L))
            }
            val product =
                Product.register(
                    category = mid,
                    sellerId = sellerUserId,
                    name = "신고 대상 상품",
                    description = null,
                    representativeImageKey = null,
                    regularPrice = 10_000L,
                    discountPrice = null,
                    discountStartAt = null,
                    discountEndAt = null,
                )
            em.persist(product)
            product.publicId
        }!!

    private fun changeSaleStatus(
        publicId: String,
        status: SaleStatus,
    ) {
        tx.execute {
            em
                .createQuery("update Product p set p.saleStatus = :status where p.publicId = :publicId")
                .setParameter("status", status)
                .setParameter("publicId", publicId)
                .executeUpdate()
        }
    }

    /** 공개 상태 리뷰를 심고 id를 돌려준다. order_item_id UNIQUE 때문에 주문 품목 id는 리뷰마다 다르다 */
    private fun persistReview(authorUserId: Long): Long =
        tx.execute {
            val review =
                Review.write(
                    productId = 1L,
                    optionNameSnapshot = "기본",
                    orderItemId = nextOrderItemId++,
                    authorUserId = authorUserId,
                    rating = 5,
                    content = "좋은 상품입니다",
                )
            em.persist(review)
            review.id
        }!!

    private fun changeReviewStatus(
        reviewId: Long,
        status: ReviewStatus,
    ) {
        tx.execute {
            em
                .createQuery("update Review r set r.reviewStatus = :status where r.id = :id")
                .setParameter("status", status)
                .setParameter("id", reviewId)
                .executeUpdate()
        }
    }

    private fun createPendingUser(): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            em.flush()
            user.id
        }!!

    private fun withdraw(userId: Long) {
        tx.execute { em.find(User::class.java, userId).withdraw() }
    }

    private fun productReports(): List<ProductReport> =
        tx.execute {
            em.createQuery("select r from ProductReport r join fetch r.product order by r.id", ProductReport::class.java).resultList
        }!!

    private fun reviewReports(): List<ReviewReport> =
        tx.execute {
            em.createQuery("select r from ReviewReport r join fetch r.review order by r.id", ReviewReport::class.java).resultList
        }!!

    private fun userReports(): List<UserReport> =
        tx.execute {
            em
                .createQuery(
                    "select r from UserReport r join fetch r.reporter join fetch r.targetUser order by r.id",
                    UserReport::class.java,
                ).resultList
        }!!

    private inline fun <reified T : Any> recorded(): List<T> = applicationEvents.stream(T::class.java).toList()

    /** 세 신고 테이블이 비어 있고 접수 이벤트도 발행되지 않았는지 확인한다 */
    private fun assertNothingReported() {
        assertTrue(productReports().isEmpty(), "상품 신고가 저장되면 안 된다")
        assertTrue(reviewReports().isEmpty(), "리뷰 신고가 저장되면 안 된다")
        assertTrue(userReports().isEmpty(), "사용자 신고가 저장되면 안 된다")
        assertTrue(recorded<ProductReportReceivedEvent>().isEmpty(), "상품 신고 접수 이벤트가 발행되면 안 된다")
        assertTrue(recorded<ReviewReportReceivedEvent>().isEmpty(), "리뷰 신고 접수 이벤트가 발행되면 안 된다")
        assertTrue(recorded<UserReportReceivedEvent>().isEmpty(), "사용자 신고 접수 이벤트가 발행되면 안 된다")
    }
}
