package com.aechak.seller.product

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.service.InspectionQueryService
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.InspectionFinding
import com.aechak.domain.inspection.ProductReviewQueueItem
import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.PerspectiveOutcome
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import com.aechak.domain.inspection.repository.ProductReviewQueueRepository
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.seller.seller.Seller
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Duration
import java.time.LocalDateTime

/**
 * [통합] 셀러가 반려 사유를 확인하는 조회를 고정한다.
 *
 * 여기서 잡는 것은 **고칠 수 있는 반려인가**다. 판정을 적어 두기만 하고 사유를 돌려주지
 * 않으면 셀러는 상태만 보고 무엇을 고쳐야 하는지 알 수 없어서, 반려가 사실상 차단이 된다.
 *
 * 검수 행을 직접 만든다. 발행 경로를 타지 않아도 되는 읽기 전용 기능이라 기존 컨텍스트를
 * 그대로 쓸 수 있다.
 */
class SellerProductInspectionReasonIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var inspections: ContentInspectionRepository

    @Autowired
    private lateinit var reviewQueue: ProductReviewQueueRepository

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String
    private var productId = 0L
    private lateinit var publicId: String
    private var leafCategoryId = 0L

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
            em.persist(Seller.open(sellerUserId, "애착상회", 0L))
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            leafCategoryId = leaf.id

            val product = newProduct(leaf, sellerUserId)
            em.persist(product)
            productId = product.id
            publicId = product.publicId
        }
    }

    @Test
    fun `반려 사유와 안내 문구를 돌려준다`() {
        settledInspection(
            hash = "a".repeat(64),
            judgment = InspectionJudgment.FLAG,
            reasonCode = "MEDICAL_CLAIM",
            reasonSummary = "관절염 치료에 효과",
            sellerNotice = "치료 효능 표현을 고쳐 주세요.",
        )

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.productId").value(publicId))
            .andExpect(jsonPath("$.data.judgment").value("FLAG"))
            .andExpect(jsonPath("$.data.reasonCode").value("MEDICAL_CLAIM"))
            .andExpect(jsonPath("$.data.reasonSummary").value("관절염 치료에 효과"))
            .andExpect(jsonPath("$.data.sellerNotice").value("치료 효능 표현을 고쳐 주세요."))
            .andExpect(jsonPath("$.data.settledAt").exists())
    }

    @Test
    fun `검수를 한 번도 타지 않은 상품은 검수 부분이 비어 온다`() {
        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.inspectionStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.progressStep").value("APPROVED"))
            .andExpect(jsonPath("$.data.judgment").doesNotExist())
            .andExpect(jsonPath("$.data.sellerNotice").doesNotExist())
    }

    @Test
    fun `가장 최근 검수를 돌려준다`() {
        settledInspection(
            hash = "b".repeat(64),
            judgment = InspectionJudgment.FLAG,
            reasonCode = "MEDICAL_CLAIM",
            reasonSummary = "관절염 치료에 효과",
            sellerNotice = "치료 효능 표현을 고쳐 주세요.",
        )
        settledInspection(
            hash = "c".repeat(64),
            judgment = InspectionJudgment.PASS,
            reasonCode = null,
            reasonSummary = null,
            sellerNotice = null,
        )

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.judgment").value("PASS"))
            .andExpect(jsonPath("$.data.sellerNotice").doesNotExist())
    }

    @Test
    fun `AI가 보는 중이면 진행 단계가 AI 검수다`() {
        tx.execute {
            val inspection = inspections.save(queuedInspection("d".repeat(64)))
            inspection.lease("test", LocalDateTime.now(), Duration.ofMinutes(1), Duration.ofMinutes(1))
            inspections.save(inspection)
        }

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.progressStep").value("AI_REVIEWING"))
    }

    @Test
    fun `AI가 반려하면 진행 단계가 반려이고 사유는 AI가 찾은 문구로만 돌려준다`() {
        changeStatus(InspectionStatus.REJECTED)
        settledInspection(
            hash = "e".repeat(64),
            judgment = InspectionJudgment.FLAG,
            reasonCode = "MEDICAL_CLAIM",
            reasonSummary = "관절염 치료에 효과",
            sellerNotice = "치료 효능 표현을 고쳐 주세요.",
        )
        openReview()

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.progressStep").value("REJECTED"))
            .andExpect(jsonPath("$.data.reasonSummary").value("관절염 치료에 효과"))
            .andExpect(jsonPath("$.data.sellerNotice").value("치료 효능 표현을 고쳐 주세요."))
            .andExpect(jsonPath("$.data.rejectReason").doesNotExist())
    }

    @Test
    fun `관리자가 반려한 상품을 고쳐 통과하면 진행 단계가 관리자 확인이다`() {
        changeStatus(InspectionStatus.REJECTED)
        rejectedByAdmin("효능 표현을 빼고 다시 등록해 주세요.")
        settledInspection(
            hash = "a".repeat(64),
            judgment = InspectionJudgment.PASS,
            reasonCode = null,
            reasonSummary = null,
            sellerNotice = null,
        )
        openReview()

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.progressStep").value("ADMIN_REVIEWING"))
    }

    @Test
    fun `관리자가 반려했다가 승인한 뒤 AI가 반려하면 지난 관리자 사유를 싣지 않는다`() {
        rejectedByAdmin("효능 표현을 빼고 다시 등록해 주세요.")
        tx.execute {
            val review = openReview()
            review.approve(LocalDateTime.now())
            reviewQueue.save(review)
        }
        changeStatus(InspectionStatus.REJECTED)
        settledInspection(
            hash = "b".repeat(64),
            judgment = InspectionJudgment.FLAG,
            reasonCode = "MEDICAL_CLAIM",
            reasonSummary = "관절염 치료에 효과",
            sellerNotice = "치료 효능 표현을 고쳐 주세요.",
        )
        openReview()

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.progressStep").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectReason").doesNotExist())
    }

    @Test
    fun `관리자가 반려하면 진행 단계가 반려이고 관리자가 적은 사유를 돌려준다`() {
        changeStatus(InspectionStatus.REJECTED)
        settledInspection(
            hash = "f".repeat(64),
            judgment = InspectionJudgment.FLAG,
            reasonCode = "MEDICAL_CLAIM",
            reasonSummary = "관절염 치료에 효과",
            sellerNotice = "치료 효능 표현을 고쳐 주세요.",
        )
        tx.execute {
            val review = openReview()
            review.reject("효능 표현을 빼고 다시 등록해 주세요.", LocalDateTime.now())
            reviewQueue.save(review)
        }

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.progressStep").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectReason").value("효능 표현을 빼고 다시 등록해 주세요."))
    }

    @Test
    fun `반려된 적이 있어도 지금 반려 상태가 아니면 반려 사유를 싣지 않는다`() {
        tx.execute {
            val review = openReview()
            review.reject("효능 표현을 빼고 다시 등록해 주세요.", LocalDateTime.now())
            reviewQueue.save(review)
        }

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.progressStep").value("APPROVED"))
            .andExpect(jsonPath("$.data.rejectReason").doesNotExist())
    }

    @Test
    fun `남의 상품은 없는 것으로 답한다`() {
        val otherSellerId = createActiveUser()
        val otherPublicId =
            tx.execute {
                em.persist(Seller.open(otherSellerId, "다른상회", 0L))
                val leaf = em.find(Category::class.java, leafCategoryId)
                val product = newProduct(leaf, otherSellerId)
                em.persist(product)
                product.publicId
            }!!

        mockMvc
            .perform(inspectionRequest(otherPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))
    }

    @Test
    fun `위반 목록을 분류와 구절과 제안으로 돌려준다`() {
        givenFlagged()
        seedFinding(
            InspectionPerspectiveKind.DISPLAY_ADS,
            """[{"category":"MEDICAL_CLAIM","perspective":"DISPLAY_ADS","spanStart":0,"spanEnd":6,"quote":"슬개골 완치","suggestion":"건강 관리에 도움을 줄 수 있는","confidence":null}]""",
        )

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.violations.length()").value(1))
            .andExpect(jsonPath("$.data.violations[0].category").value("MEDICAL_CLAIM"))
            .andExpect(jsonPath("$.data.violations[0].quote").value("슬개골 완치"))
            .andExpect(jsonPath("$.data.violations[0].suggestion").value("건강 관리에 도움을 줄 수 있는"))
    }

    @Test
    fun `위반 항목에 내부 값을 싣지 않는다`() {
        givenFlagged()
        seedFinding(
            InspectionPerspectiveKind.DISPLAY_ADS,
            """[{"category":"MEDICAL_CLAIM","perspective":"DISPLAY_ADS","spanStart":0,"spanEnd":6,"quote":"슬개골 완치","suggestion":null,"confidence":0.9}]""",
        )

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.violations[0].perspective").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.data.violations[0].spanStart").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.data.violations[0].spanEnd").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.data.violations[0].confidence").doesNotHaveJsonPath())
    }

    @Test
    fun `제안이 없는 위반은 suggestion을 null로 돌려준다`() {
        givenFlagged()
        seedFinding(InspectionPerspectiveKind.RULE, """[{"category":"BANNED_INGREDIENT","quote":"자일리톨","suggestion":null}]""")

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.violations[0].suggestion").hasJsonPath())
            .andExpect(jsonPath("$.data.violations[0].suggestion").value(nullValue()))
    }

    @Test
    fun `검수를 한 번도 타지 않은 상품은 위반 목록이 빈 배열이다`() {
        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.violations").isArray)
            .andExpect(jsonPath("$.data.violations").isEmpty)
    }

    @Test
    fun `저장값 모양이 맞지 않는 행이 있어도 사유와 나머지 위반을 돌려준다`() {
        givenFlagged()
        seedFinding(InspectionPerspectiveKind.DISPLAY_ADS, """{"foo":1}""")
        seedFinding(InspectionPerspectiveKind.CONDUCT_TRADE, """[{"category":"SPAM","quote":"도배","suggestion":null}]""")

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.reasonSummary").value("슬개골 완치"))
            .andExpect(jsonPath("$.data.violations.length()").value(1))
            .andExpect(jsonPath("$.data.violations[0].quote").value("도배"))
    }

    @Test
    fun `모르는 분류 값도 문자열 그대로 돌려준다`() {
        givenFlagged()
        seedFinding(InspectionPerspectiveKind.DISPLAY_ADS, """[{"category":"NEW_KIND","quote":"새 분류","suggestion":null}]""")

        mockMvc
            .perform(inspectionRequest(publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.violations[0].category").value("NEW_KIND"))
    }

    @Test
    fun `저장값을 읽지 못하면 경고 로그에 검수 id만 남긴다`() {
        givenFlagged()
        val inspectionId = seedFinding(InspectionPerspectiveKind.DISPLAY_ADS, """{"quote":"010-1234-5678"}""")

        val warnings =
            captureInspectionWarnings {
                mockMvc.perform(inspectionRequest(publicId)).andExpect(status().isOk)
            }

        assertTrue(warnings.any { it.formattedMessage.contains(inspectionId.toString()) }, "어느 검수를 못 읽었는지 남아야 한다")
        assertTrue(warnings.none { it.formattedMessage.contains("010-1234-5678") }, "걸린 구절에 연락처가 있을 수 있어 저장값을 남기면 안 된다")
        assertTrue(warnings.all { it.throwableProxy == null }, "예외 메시지가 저장값 조각을 물고 올 수 있어 예외를 남기면 안 된다")
    }

    private fun givenFlagged() {
        settledInspection(
            hash = "a".repeat(64),
            judgment = InspectionJudgment.FLAG,
            reasonCode = "MEDICAL_CLAIM",
            reasonSummary = "슬개골 완치",
            sellerNotice = "치료 효능 표현을 고쳐 주세요.",
        )
    }

    /** 가장 최근 검수에 관점별 행을 심고 그 검수 id를 돌려준다 */
    private fun seedFinding(
        perspective: InspectionPerspectiveKind,
        stored: String,
    ): Long =
        tx.execute {
            val inspectionId = inspections.findLatestByTarget(InspectionTargetType.PRODUCT, productId)!!.id
            em.persist(InspectionFinding.of(inspectionId, perspective, PerspectiveOutcome.VIOLATION, violations = stored))
            inspectionId
        }!!

    private fun captureInspectionWarnings(block: () -> Unit): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(InspectionQueryService::class.java.packageName) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            block()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.filter { it.level == Level.WARN }
    }

    private fun inspectionRequest(target: String) =
        get("/api/v1/sellers/me/products/$target/inspection")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun newProduct(
        category: Category,
        ownerId: Long,
    ): Product =
        Product.register(
            category = category,
            sellerId = ownerId,
            name = "연어 건사료 2kg",
            description = "우리 아이 주식으로 좋습니다.",
            representativeImageKey = "products/thumbnail.png",
            regularPrice = 25_000L,
            discountPrice = null,
            discountStartAt = null,
            discountEndAt = null,
            additionalImageKeys = emptyList(),
            detailImageKeys = emptyList(),
        )

    private fun changeStatus(status: InspectionStatus) {
        tx.execute {
            em
                .createQuery("update Product p set p.inspectionStatus = :status where p.id = :id")
                .setParameter("status", status)
                .setParameter("id", productId)
                .executeUpdate()
        }
    }

    private fun openReview(): ProductReviewQueueItem =
        tx.execute {
            reviewQueue.save(
                ProductReviewQueueItem.openForProduct(productId, sellerUserId, "연어 건사료 2kg", LocalDateTime.now()),
            )
        }!!

    private fun rejectedByAdmin(reason: String) {
        tx.execute {
            val review = openReview()
            review.reject(reason, LocalDateTime.now())
            reviewQueue.save(review)
        }
    }

    private fun queuedInspection(hash: String): ContentInspection =
        ContentInspection.queue(
            targetType = InspectionTargetType.PRODUCT,
            targetRef = productId,
            contentHash = hash,
            policyVersion = "policy-v1",
            targetVersionNo = 1,
        )

    private fun settledInspection(
        hash: String,
        judgment: InspectionJudgment,
        reasonCode: String?,
        reasonSummary: String?,
        sellerNotice: String?,
    ) {
        tx.execute {
            val inspection =
                inspections.save(
                    ContentInspection.queue(
                        targetType = InspectionTargetType.PRODUCT,
                        targetRef = productId,
                        contentHash = hash,
                        policyVersion = "policy-v1",
                        targetVersionNo = 1,
                    ),
                )
            inspection.settle(
                judgment = judgment,
                promptVersion = "inspection-v1+policy-v1",
                reasonCode = reasonCode,
                reasonSummary = reasonSummary,
                sellerNotice = sellerNotice,
                now = LocalDateTime.now(),
            )
            inspections.save(inspection)
        }
    }
}
