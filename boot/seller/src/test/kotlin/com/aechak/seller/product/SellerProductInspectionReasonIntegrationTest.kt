package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.seller.seller.Seller
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
