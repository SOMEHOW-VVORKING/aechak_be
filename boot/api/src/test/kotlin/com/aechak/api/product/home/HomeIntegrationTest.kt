package com.aechak.api.product.home

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.like.ProductLike
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

/**
 * 홈 화면 조회(GET /home) 통합 테스트 — 깨지면 홈의 노출 정책이나 인기 정렬 계약이 바뀐 것이다.
 *
 * 인기 랭킹은 정렬이 결정적이라 순서까지 단언하고, 추천은 무작위라 순서 대신 대상 집합과 개수만 본다.
 * 노출 개수는 FE가 화면을 그리는 전제라 상수 참조 대신 계약값 5와 8을 직접 단언한다.
 * 홈은 아직 비회원에게 열지 않아 인증이 필요하다. HTTP 절이 그 정책을 고정한다.
 * 격리는 커밋 + truncate(IntegrationTestBase)이므로 픽스처는 tx.execute로 실제 커밋한다.
 */
class HomeIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var productUseCase: ProductUseCase

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

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

    // ---------- 픽스처 헬퍼 (tx.execute 안에서만 호출) ----------

    private fun persistMidCategory(name: String = "산책용품"): Category {
        val root = Category.create(null, 1, "강아지", null, 1)
        em.persist(root)
        val mid = Category.create(root, 2, name, null, 1)
        em.persist(mid)
        return mid
    }

    /**
     * 상품과 통계 행을 함께 심는다.
     * 실제 등록 경로(ProductService.register)가 상품마다 통계 행을 만들기 때문에, 픽스처도 같은 상태를 만들어야
     * 프로덕션에 존재할 수 없는 상태를 계약으로 굳히지 않는다.
     */
    private fun persistProduct(
        category: Category,
        name: String,
        sellerId: Long = defaultSellerId,
        reviewCount: Int = 0,
        likeCount: Long = 0L,
        discountPrice: Long? = null,
        discountStartAt: LocalDateTime? = null,
        discountEndAt: LocalDateTime? = null,
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
                discountPrice = discountPrice,
                discountStartAt = discountStartAt,
                discountEndAt = discountEndAt,
            )
        em.persist(product)
        em.flush()
        em.persist(ProductStats.create(product.id))
        em.flush()
        // 집계는 저장소 조건부 원자 UPDATE로만 갱신되므로 픽스처도 세터 대신 bulk update로 만든다
        em
            .createQuery("update ProductStats s set s.reviewCount = :reviewCount, s.likeCount = :likeCount where s.productId = :id")
            .setParameter("reviewCount", reviewCount)
            .setParameter("likeCount", likeCount)
            .setParameter("id", product.id)
            .executeUpdate()
        return product
    }

    private fun ensureActiveSeller(sellerId: Long) {
        if (em.find(Seller::class.java, sellerId) == null) {
            em.persist(Seller.open(userId = sellerId, storeName = "store-$sellerId", baseShippingFee = 3000L))
        }
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

    // ---------- 인기 랭킹 정렬 ----------

    @Test
    fun `랭킹은 리뷰 수가 많은 순으로 반환한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "리뷰3", reviewCount = 3)
            persistProduct(mid, "리뷰10", reviewCount = 10)
            persistProduct(mid, "리뷰0", reviewCount = 0)
        }

        val ranking = productUseCase.getCuration(null).ranking

        assertEquals(listOf("리뷰10", "리뷰3", "리뷰0"), ranking.map { it.name }, "리뷰 수 내림차순이 아니다")
    }

    @Test
    fun `리뷰 수가 같으면 찜 수가 많은 순으로 가른다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "찜1", reviewCount = 5, likeCount = 1L)
            persistProduct(mid, "찜9", reviewCount = 5, likeCount = 9L)
        }

        val ranking = productUseCase.getCuration(null).ranking

        assertEquals(listOf("찜9", "찜1"), ranking.map { it.name }, "리뷰 수 동률을 찜 수로 가르지 않는다")
    }

    @Test
    fun `리뷰 수와 찜 수가 모두 같으면 최근 등록순으로 가른다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "먼저등록", reviewCount = 2, likeCount = 2L)
            persistProduct(mid, "나중등록", reviewCount = 2, likeCount = 2L)
        }

        val ranking = productUseCase.getCuration(null).ranking

        assertEquals(listOf("나중등록", "먼저등록"), ranking.map { it.name }, "최종 tie-break가 id 내림차순이 아니다")
    }

    @Test
    fun `랭킹은 상위 5개까지만 반환한다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..8).forEach { persistProduct(mid, "상품$it") }
        }

        val ranking = productUseCase.getCuration(null).ranking

        assertEquals(5, ranking.size, "홈 랭킹 노출 개수 계약이 5가 아니다")
    }

    // ---------- 랭킹 노출 정책 ----------

    @Test
    fun `품절 상품도 랭킹에 노출한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "판매중")
            val soldOut = persistProduct(mid, "품절")
            em.flush()
            overrideSaleStatus(soldOut.id, SaleStatus.OUT_OF_STOCK)
        }

        val ranking = productUseCase.getCuration(null).ranking

        assertEquals(setOf("판매중", "품절"), ranking.map { it.name }.toSet(), "품절 상품이 랭킹에서 빠졌다")
        assertEquals(
            SaleStatus.OUT_OF_STOCK,
            ranking.first { it.name == "품절" }.saleStatus,
            "품절 표기용 판매 상태가 그대로 실리지 않았다",
        )
    }

    @Test
    fun `미승인 판매중지 판매종료 상품은 랭킹에서 제외한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "정상")
            val suspended = persistProduct(mid, "판매중지")
            val ended = persistProduct(mid, "판매종료")
            val pending = persistProduct(mid, "검수대기")
            em.flush()
            overrideSaleStatus(suspended.id, SaleStatus.SUSPENDED)
            overrideSaleStatus(ended.id, SaleStatus.ENDED)
            overrideInspectionStatus(pending.id, InspectionStatus.PENDING)
        }

        assertEquals(
            listOf("정상"),
            productUseCase.getCuration(null).ranking.map { it.name },
            "노출 대상이 아닌 상품이 랭킹에 들어왔다",
        )
    }

    @Test
    fun `셀러나 카테고리가 비활성인 상품은 랭킹에서 제외한다`() {
        tx.execute {
            val mid = persistMidCategory()
            val inactiveCategory = persistMidCategory("비활성분류")
            persistProduct(mid, "정상")
            persistProduct(mid, "비활성셀러", sellerId = 88L)
            persistProduct(inactiveCategory, "비활성카테고리")
            em.flush()
            overrideSellerStatus(88L, SellerStatus.SUSPENDED)
            overrideCategoryStatus(inactiveCategory.id, CategoryStatus.INACTIVE)
        }

        assertEquals(
            listOf("정상"),
            productUseCase.getCuration(null).ranking.map { it.name },
            "셀러나 카테고리 비활성 조건이 랭킹에 반영되지 않았다",
        )
    }

    // ---------- 추천 ----------

    @Test
    fun `추천은 판매중 상품만 반환한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "판매중")
            val soldOut = persistProduct(mid, "품절")
            em.flush()
            overrideSaleStatus(soldOut.id, SaleStatus.OUT_OF_STOCK)
        }

        assertEquals(
            listOf("판매중"),
            productUseCase.getCuration(null).recommended.map { it.name },
            "품절 상품이 추천에 섞였다",
        )
    }

    @Test
    fun `판매중 상품이 노출 개수보다 적으면 있는 만큼만 반환한다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..3).forEach { persistProduct(mid, "상품$it") }
        }

        assertEquals(3, productUseCase.getCuration(null).recommended.size, "있는 만큼 반환하지 않았다")
    }

    @Test
    fun `추천은 8개를 무작위로 뽑는다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..20).forEach { persistProduct(mid, "상품$it") }
        }

        // 20개에서 8개를 뽑을 때 두 표본이 완전히 겹칠 확률은 1/C(20,8)로 12만분의 1이라 3회면 충분하다.
        // 고정 정렬이면 매번 같은 8개만 나와 합집합이 8에서 멈춘다.
        val seen = mutableSetOf<String>()
        repeat(3) {
            val recommended = productUseCase.getCuration(null).recommended
            assertEquals(8, recommended.size, "홈 추천 노출 개수 계약이 8이 아니다")
            seen += recommended.map { it.name }
        }

        assertTrue(seen.size > 8, "무작위 정렬이 적용되지 않았다. 3회 동안 관측된 상품 수=${seen.size}")
    }

    // ---------- 찜 여부 ----------

    @Test
    fun `로그인 사용자는 찜한 상품만 isLiked가 참이다`() {
        val likerId = createActiveUser()
        tx.execute {
            val mid = persistMidCategory()
            val liked = persistProduct(mid, "찜함")
            persistProduct(mid, "찜안함")
            em.flush()
            em.persist(ProductLike.of(liked, likerId))
        }

        val home = productUseCase.getCuration(likerId)

        listOf("랭킹" to home.ranking, "추천" to home.recommended).forEach { (section, cards) ->
            val byName = cards.associateBy { it.name }
            assertTrue(byName.getValue("찜함").isLiked, "$section 섹션에서 찜한 상품이 isLiked=false다")
            assertFalse(byName.getValue("찜안함").isLiked, "$section 섹션에서 찜하지 않은 상품이 isLiked=true다")
        }
    }

    @Test
    fun `userId가 없으면 누군가 찜한 상품이라도 isLiked가 모두 거짓이다`() {
        tx.execute {
            val mid = persistMidCategory()
            val liked = persistProduct(mid, "찜함")
            em.flush()
            em.persist(ProductLike.of(liked, 42L))
        }

        val home = productUseCase.getCuration(null)

        assertTrue(home.ranking.none { it.isLiked }, "userId가 없는데 랭킹에 찜 여부가 샜다")
        assertTrue(home.recommended.none { it.isLiked }, "userId가 없는데 추천에 찜 여부가 샜다")
    }

    // ---------- 빈 상태와 표시가 ----------

    @Test
    fun `노출할 상품이 없으면 두 섹션 모두 빈 목록이다`() {
        val home = productUseCase.getCuration(null)

        assertTrue(home.ranking.isEmpty(), "상품이 없는데 랭킹이 비어 있지 않다")
        assertTrue(home.recommended.isEmpty(), "상품이 없는데 추천이 비어 있지 않다")
    }

    @Test
    fun `할인 기간이 지난 상품은 정가로 표시한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(
                category = mid,
                name = "할인만료",
                discountPrice = 7000L,
                discountStartAt = LocalDateTime.now().minusDays(10),
                discountEndAt = LocalDateTime.now().minusDays(1),
            )
        }

        val card = productUseCase.getCuration(null).ranking.single()

        assertEquals(10000L, card.regularPrice, "정가가 그대로 실리지 않았다")
        assertNull(card.discountPrice, "만료된 할인가가 표시가로 나갔다")
        assertNull(card.discountRate, "만료된 할인율이 표시가로 나갔다")
    }

    // ---------- HTTP 계약과 인가 ----------

    @Test
    fun `비로그인은 홈을 조회할 수 없다`() {
        // 홈은 아직 비회원에게 열지 않는다. 여는 시점에 이 단언이 먼저 깨져 SecurityConfig를 같이 손대게 한다.
        mockMvc
            .perform(get("/api/v1/home"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `로그인 사용자가 홈을 조회하면 두 섹션이 내려온다`() {
        val userId = createActiveUser()
        tx.execute { persistMidCategory().let { persistProduct(it, "판매중") } }

        mockMvc
            .perform(get("/api/v1/home").header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.ranking[0].name").value("판매중"))
            .andExpect(jsonPath("$.data.ranking[0].isLiked").value(false))
            .andExpect(jsonPath("$.data.recommended[0].name").value("판매중"))
    }

    @Test
    fun `상품이 없어도 두 섹션을 빈 배열로 내려준다`() {
        val userId = createActiveUser()

        mockMvc
            .perform(get("/api/v1/home").header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.ranking").isEmpty)
            .andExpect(jsonPath("$.data.recommended").isEmpty)
    }

    @Test
    fun `홈 카드에는 찜 여부가 실린다`() {
        val userId = createActiveUser()
        tx.execute {
            val product = persistMidCategory().let { persistProduct(it, "찜함") }
            em.flush()
            em.persist(ProductLike.of(product, userId))
        }

        mockMvc
            .perform(get("/api/v1/home").header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.ranking[0].isLiked").value(true))
    }
}
