package com.aechak.seller.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.pii.support.PiiStringCodec
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

/**
 * 셀러 클레임 목록·상세·운영 메모 통합. 서버는 네 축을 접지 않고 그대로 내리는 것이 계약이므로
 * 응답 단언도 축별로 본다. 환불 예정액은 요청 시점 계산기를 거치므로 적립금을 쓴 주문까지 함께 본다.
 */
class SellerClaimQueryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var piiStringCodec: PiiStringCodec

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String
    private var sig = 0

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        openSeller(sellerUserId)
    }

    @Test
    fun `목록은 내 클레임만 최신순으로 내리고 타 셀러 클레임은 포함하지 않는다`() {
        val other = createActiveUser().also { openSeller(it) }
        val first = seedClaim()
        val second = seedClaim()
        seedClaim(sellerId = other)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.claims.length()").value(2))
            .andExpect(jsonPath("$.data.claims[0].claimPublicId").value(second.claimPublicId))
            .andExpect(jsonPath("$.data.claims[1].claimPublicId").value(first.claimPublicId))
            .andExpect(jsonPath("$.data.claims[0].orderPublicId").value(second.orderPublicId))
    }

    @Test
    fun `유형 필터는 지정한 유형만 내린다`() {
        seedClaim()
        val returnClaim = seedClaim(claimType = ClaimType.RETURN)
        seedClaim(claimType = ClaimType.EXCHANGE)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?claimType=RETURN").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.claims[0].claimPublicId").value(returnClaim.claimPublicId))
            .andExpect(jsonPath("$.data.claims[0].claimType").value("RETURN"))
    }

    @Test
    fun `구매자가 적어 보낸 사유 코드와 상세가 목록과 상세 양쪽에 내려온다`() {
        // 셀러는 이 사유를 읽고 반품비를 누가 부담할지 정한다. 응답 조립이 위치 인자 프로젝션이라
        // 필드 순서가 뒤틀리면 조용히 다른 값이 실리므로, 두 필드를 여기서 못박는다.
        val seeded = seedClaim()

        mockMvc
            .perform(get("/api/v1/sellers/me/claims").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claims[0].reasonCode").value("CHANGE_OF_MIND"))
            .andExpect(jsonPath("$.data.claims[0].reasonDetail").value("사이즈를 잘못 골랐어요"))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims/${seeded.claimPublicId}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claim.reasonCode").value("CHANGE_OF_MIND"))
            .andExpect(jsonPath("$.data.claim.reasonDetail").value("사이즈를 잘못 골랐어요"))
    }

    @Test
    fun `상태와 환불 상태 필터는 축별로 걸린다`() {
        seedClaim()
        val approved = seedClaim().also { approve(it) }
        val refunded = seedClaim().also { approve(it) }.also { markRefunded(it) }

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?status=APPROVED").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(2))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?refundStatus=DONE").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.claims[0].claimPublicId").value(refunded.claimPublicId))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?status=APPROVED&refundStatus=NONE").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.claims[0].claimPublicId").value(approved.claimPublicId))
    }

    @Test
    fun `요청일 필터는 종료일 당일까지 포함한다`() {
        val old = seedClaim().also { forceRequestedAt(it, LocalDateTime.of(2026, 3, 1, 10, 0)) }
        val edge = seedClaim().also { forceRequestedAt(it, LocalDateTime.of(2026, 3, 5, 23, 59, 59)) }

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?requestedFrom=2026-03-02&requestedTo=2026-03-05").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.claims[0].claimPublicId").value(edge.claimPublicId))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?requestedTo=2026-03-01").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.claims[0].claimPublicId").value(old.claimPublicId))
    }

    @Test
    fun `모르는 필터 값과 역전된 기간과 범위 밖 size는 400이다`() {
        mockMvc
            .perform(get("/api/v1/sellers/me/claims?status=NOPE").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?requestedFrom=2026-03-05&requestedTo=2026-03-01").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?size=101").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `네 자리 연도를 넘는 요청일은 400이다`() {
        // LocalDate.parse가 받아버리는 확장 연도. 반개구간 상한을 만드는 하루 더하기가 경계에서 터져 500이 되던 입력이다.
        mockMvc
            .perform(get("/api/v1/sellers/me/claims?requestedTo=%2B999999999-12-31").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?requestedFrom=-999999999-01-01").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `유형별 건수는 유형 필터를 무시하고 상태 필터는 반영한다`() {
        seedClaim()
        seedClaim()
        seedClaim(claimType = ClaimType.RETURN)
        val approvedExchange = seedClaim(claimType = ClaimType.EXCHANGE)
        approve(approvedExchange)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?claimType=CANCEL").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.counts.CANCEL").value(2))
            .andExpect(jsonPath("$.data.counts.RETURN").value(1))
            .andExpect(jsonPath("$.data.counts.EXCHANGE").value(1))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?status=APPROVED").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.counts.CANCEL").value(0))
            .andExpect(jsonPath("$.data.counts.RETURN").value(0))
            .andExpect(jsonPath("$.data.counts.EXCHANGE").value(1))
    }

    @Test
    fun `페이지 경계는 totalPages와 hasNext로 드러나고 마지막 페이지를 넘기면 빈 목록이다`() {
        repeat(3) { seedClaim() }

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?page=0&size=2").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claims.length()").value(2))
            .andExpect(jsonPath("$.data.totalCount").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.hasNext").value(true))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?page=1&size=2").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claims.length()").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?page=5&size=2").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claims.length()").value(0))
            .andExpect(jsonPath("$.data.totalCount").value(3))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `네 축은 접히지 않고 그대로 내려간다`() {
        val seeded = seedClaim()
        approve(seeded)
        markRefunded(seeded)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claims[0].status").value("APPROVED"))
            .andExpect(jsonPath("$.data.claims[0].refundStatus").value("DONE"))
            .andExpect(jsonPath("$.data.claims[0].sellerApprovalStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.claims[0].settlementStatus").value("PENDING"))
    }

    @Test
    fun `반품비 부담 3필드는 유형과 발송 여부와 승인 결과를 그대로 드러낸다`() {
        val cancel = seedClaim()
        val returned = seedClaim(claimType = ClaimType.RETURN)
        dispatch(returned)
        forceBearer(returned, ReturnCostBearer.SELLER)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?claimType=CANCEL").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claims[0].claimPublicId").value(cancel.claimPublicId))
            .andExpect(jsonPath("$.data.claims[0].dispatched").value(false))
            .andExpect(jsonPath("$.data.claims[0].returnShippingFee").value(nullValue()))
            .andExpect(jsonPath("$.data.claims[0].returnCostBearer").value(nullValue()))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims?claimType=RETURN").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claims[0].dispatched").value(true))
            .andExpect(jsonPath("$.data.claims[0].returnShippingFee").value(0))
            .andExpect(jsonPath("$.data.claims[0].returnCostBearer").value("SELLER"))
    }

    @Test
    fun `전량 취소 환불 예정액은 품목 환불액 합에 배송비를 더한 값이다`() {
        seedClaim(itemRefundAmount = 25_000L, shippingFee = 3_000L)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claims[0].refundAmount").value(28_000))
            .andExpect(jsonPath("$.data.claims[0].itemCount").value(1))
            .andExpect(jsonPath("$.data.claims[0].productNames[0]").value("멍냥 사료"))
    }

    @Test
    fun `품목 일부만 대상인 클레임은 배송비를 더하지 않는다`() {
        seedClaim(itemCount = 2, itemRefundAmount = 10_000L, shippingFee = 3_000L, targetFirstItemOnly = true)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claims[0].refundAmount").value(10_000))
            .andExpect(jsonPath("$.data.claims[0].itemCount").value(1))
    }

    @Test
    fun `적립금을 쓴 주문의 전량 취소 환불 예정액은 실결제액과 같다`() {
        // 상품 10,000 + 배송비 3,000 에 적립금 1,000 → 실결제 12,000.
        // 안분은 품목 770 / 배송비 230 이라 저장된 품목 환불액이 9,230 이고 배송비 환불액이 2,770 이다.
        seedClaim(itemRefundAmount = 9_230L, unitPrice = 10_000L, shippingFee = 3_000L, usedPoint = 1_000L)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claims[0].refundAmount").value(12_000))
    }

    @Test
    fun `상세는 품목과 주문 수령자 정보와 환불 예정액을 함께 내린다`() {
        val seeded = seedClaim(itemRefundAmount = 25_000L, shippingFee = 3_000L)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims/${seeded.claimPublicId}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.claim.claimPublicId").value(seeded.claimPublicId))
            .andExpect(jsonPath("$.data.claim.refundAmount").value(28_000))
            .andExpect(jsonPath("$.data.order.orderPublicId").value(seeded.orderPublicId))
            .andExpect(jsonPath("$.data.order.orderStatus").value("PAID"))
            .andExpect(jsonPath("$.data.order.recipientName").value("홍길동"))
            .andExpect(jsonPath("$.data.order.contactNumber").value("01012341234"))
            .andExpect(jsonPath("$.data.order.zipCode").value("06236"))
            .andExpect(jsonPath("$.data.order.sellerShippingFee").value(3_000))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].productName").value("멍냥 사료"))
            .andExpect(jsonPath("$.data.items[0].optionName").value("기본 / 1개"))
            .andExpect(jsonPath("$.data.items[0].quantity").value(1))
            .andExpect(jsonPath("$.data.items[0].refundAmount").value(25_000))
            .andExpect(jsonPath("$.data.items[0].itemStatus").value("ORDERED"))
    }

    @Test
    fun `상세 이력은 클레임 시각 컬럼에서 시간 순으로 조립된다`() {
        val seeded = seedClaim()
        approve(seeded)
        markRefunded(seeded)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims/${seeded.claimPublicId}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.history.length()").value(3))
            .andExpect(jsonPath("$.data.history[0].event").value("REQUESTED"))
            .andExpect(jsonPath("$.data.history[1].event").value("APPROVED"))
            .andExpect(jsonPath("$.data.history[2].event").value("REFUNDED"))
    }

    @Test
    fun `타 셀러 클레임은 목록에도 상세에도 메모에도 닿지 않는다`() {
        val other = createActiveUser().also { openSeller(it) }
        val foreign = seedClaim(sellerId = other)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims/${foreign.claimPublicId}").bearer(token))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50400))

        mockMvc
            .perform(memoRequest(token, foreign.claimPublicId, """{"sellerMemo":"남의 클레임"}"""))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50400))

        assertNull(claimMemoOf(foreign.claimPublicId))
    }

    @Test
    fun `운영 메모는 저장하면 목록과 상세에 함께 나온다`() {
        val seeded = seedClaim()

        mockMvc
            .perform(memoRequest(token, seeded.claimPublicId, """{"sellerMemo":"  고객 요청으로 환불 처리  "}"""))
            .andExpect(status().isNoContent)

        assertEquals("고객 요청으로 환불 처리", claimMemoOf(seeded.claimPublicId), "앞뒤 공백은 저장하지 않는다")
        mockMvc
            .perform(get("/api/v1/sellers/me/claims").bearer(token))
            .andExpect(jsonPath("$.data.claims[0].sellerMemo").value("고객 요청으로 환불 처리"))
        mockMvc
            .perform(get("/api/v1/sellers/me/claims/${seeded.claimPublicId}").bearer(token))
            .andExpect(jsonPath("$.data.claim.sellerMemo").value("고객 요청으로 환불 처리"))
    }

    @Test
    fun `운영 메모를 공백으로 덮어쓰면 지워진다`() {
        val seeded = seedClaim()
        mockMvc
            .perform(memoRequest(token, seeded.claimPublicId, """{"sellerMemo":"남겨둘 메모"}"""))
            .andExpect(status().isNoContent)

        mockMvc
            .perform(memoRequest(token, seeded.claimPublicId, """{"sellerMemo":"   "}"""))
            .andExpect(status().isNoContent)

        assertNull(claimMemoOf(seeded.claimPublicId))
    }

    @Test
    fun `운영 메모가 501자면 400과 90001이다`() {
        val seeded = seedClaim()

        mockMvc
            .perform(memoRequest(token, seeded.claimPublicId, """{"sellerMemo":"${"가".repeat(501)}"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))

        assertNull(claimMemoOf(seeded.claimPublicId))
    }

    @Test
    fun `종결된 클레임에도 메모를 남길 수 있다`() {
        val seeded = seedClaim()
        approve(seeded)
        markRefunded(seeded)

        mockMvc
            .perform(memoRequest(token, seeded.claimPublicId, """{"sellerMemo":"정산 확인 완료"}"""))
            .andExpect(status().isNoContent)

        assertEquals("정산 확인 완료", claimMemoOf(seeded.claimPublicId))
    }

    @Test
    fun `정지된 셀러는 403과 50404이고 미인증은 401이다`() {
        val seeded = seedClaim()
        suspendSeller(sellerUserId)

        mockMvc
            .perform(get("/api/v1/sellers/me/claims").bearer(token))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(50404))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims/${seeded.claimPublicId}").bearer(token))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(50404))

        mockMvc
            .perform(memoRequest(token, seeded.claimPublicId, """{"sellerMemo":"정지 셀러"}"""))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(50404))

        mockMvc
            .perform(get("/api/v1/sellers/me/claims"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    private fun MockHttpServletRequestBuilder.bearer(accessToken: String): MockHttpServletRequestBuilder =
        header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")

    private fun memoRequest(
        accessToken: String,
        claimPublicId: String,
        body: String,
    ): MockHttpServletRequestBuilder =
        patch("/api/v1/sellers/me/claims/$claimPublicId/memo")
            .bearer(accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 0L)) }
    }

    private fun suspendSeller(userId: Long) {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :status where s.userId = :userId")
                .setParameter("status", SellerStatus.SUSPENDED)
                .setParameter("userId", userId)
                .executeUpdate()
        }
    }

    private fun claimMemoOf(claimPublicId: String): String? =
        tx.execute {
            em
                .createQuery("select c.sellerMemo from Claim c where c.publicId = :publicId", String::class.java)
                .setParameter("publicId", claimPublicId)
                .resultList
                .firstOrNull()
        }

    /** 승인 경로를 거치지 않고 승인 축만 심는다 — 승인 규칙은 이 테스트의 대상이 아니다. */
    private fun approve(seeded: SeededClaim) {
        updateClaim(
            seeded.claimPublicId,
            "c.status = :status, c.sellerApprovalStatus = :approval, c.approvedAt = :at",
            mapOf(
                "status" to ClaimStatus.APPROVED,
                "approval" to SellerApprovalStatus.APPROVED,
                "at" to LocalDateTime.now().plusMinutes(1),
            ),
        )
    }

    private fun markRefunded(seeded: SeededClaim) {
        updateClaim(
            seeded.claimPublicId,
            "c.refundStatus = :refundStatus, c.resolvedAt = :at",
            mapOf("refundStatus" to RefundStatus.DONE, "at" to LocalDateTime.now().plusMinutes(2)),
        )
    }

    private fun forceBearer(
        seeded: SeededClaim,
        bearer: ReturnCostBearer,
    ) {
        updateClaim(seeded.claimPublicId, "c.returnCostBearer = :bearer", mapOf("bearer" to bearer))
    }

    private fun forceRequestedAt(
        seeded: SeededClaim,
        at: LocalDateTime,
    ) {
        updateClaim(seeded.claimPublicId, "c.requestedAt = :at", mapOf("at" to at))
    }

    private fun updateClaim(
        claimPublicId: String,
        setClause: String,
        parameters: Map<String, Any>,
    ) {
        tx.execute {
            em
                .createQuery("update Claim c set $setClause where c.publicId = :publicId")
                .also { query -> parameters.forEach { (name, value) -> query.setParameter(name, value) } }
                .setParameter("publicId", claimPublicId)
                .executeUpdate()
        }
    }

    private fun dispatch(seeded: SeededClaim) {
        tx.execute {
            val courier = em.find(CourierMaster::class.java, COURIER_CODE) ?: CourierMaster.register(COURIER_CODE, "애착택배")
            em.merge(courier)
            val order = em.find(Order::class.java, seeded.orderId)
            em.persist(Shipment.dispatch(courier, order, "1234567890", ShipmentType.INITIAL))
        }
    }

    private data class SeededClaim(
        val claimPublicId: String,
        val orderId: Long,
        val orderPublicId: String,
    )

    /**
     * 결제완료 주문과 그 주문을 대상으로 하는 셀러 응답 대기 클레임을 심는다.
     * 반품·교환은 요청 경로가 아직 받지 않으므로 취소로 만든 뒤 유형만 바꿔 심는다.
     */
    private fun seedClaim(
        sellerId: Long = sellerUserId,
        claimType: ClaimType = ClaimType.CANCEL,
        unitPrice: Long = 25_000L,
        itemCount: Int = 1,
        itemRefundAmount: Long = 25_000L,
        shippingFee: Long = 0L,
        usedPoint: Long = 0L,
        targetFirstItemOnly: Boolean = false,
    ): SeededClaim {
        val seeded =
            tx.execute {
                val productAmount = unitPrice * itemCount
                val group =
                    OrderGroup.create(
                        buyerId = 9_999L,
                        deliveryAddressId = 0L,
                        deliveryAddress =
                            DeliveryAddressSnapshot(
                                receiverNameEnc = piiStringCodec.encrypt("홍길동"),
                                contactNumberEnc = piiStringCodec.encrypt("01012341234"),
                                zipCode = "06236",
                                baseAddress = "서울시 애착구 멍냥로 1",
                                detailAddress = "101동 1001호",
                                deliveryMemo = "문 앞에 놓아주세요",
                            ),
                        usedPoint = usedPoint,
                        totalProductAmount = productAmount,
                        totalShippingFee = shippingFee,
                        idempotencyKey = "seller-claim-${sig++}-${System.nanoTime()}",
                        expiresAt = LocalDateTime.now().plusMinutes(10),
                    )
                em.persist(group)
                val order =
                    Order.create(
                        orderGroup = group,
                        sellerId = sellerId,
                        sellerNameSnapshot = "애착상회$sellerId",
                        allocatedCouponDiscount = 0L,
                        sellerShippingFee = shippingFee,
                        baseShippingFeeSnapshot = shippingFee,
                        freeShippingThresholdSnapshot = null,
                        items = (0 until itemCount).map { orderItemOf(sellerId, unitPrice) },
                    )
                forceOrderStatus(order, OrderStatus.PAID)
                em.persist(order)
                em.flush()
                val targets = if (targetFirstItemOnly) order.items.take(1) else order.items
                val claim =
                    Claim.request(
                        order = order,
                        claimType = ClaimType.CANCEL,
                        reasonCode = "CHANGE_OF_MIND",
                        reasonDetail = "사이즈를 잘못 골랐어요",
                        items = targets.map { ClaimItem.of(it, quantity = it.quantity, refundAmount = itemRefundAmount) },
                        autoApproveDueAt = LocalDateTime.now().plusDays(2),
                    )
                em.persist(claim)
                em.flush()
                SeededClaim(claimPublicId = claim.publicId, orderId = order.id, orderPublicId = order.publicId)
            }!!
        if (claimType != ClaimType.CANCEL) {
            updateClaim(seeded.claimPublicId, "c.claimType = :claimType", mapOf("claimType" to claimType))
        }
        return seeded
    }

    private fun orderItemOf(
        sellerId: Long,
        unitPrice: Long,
    ): OrderItem {
        val category = Category.create(null, 1, "카테고리-${sig++}", null, 1)
        em.persist(category)
        val product = Product.register(category, sellerId, PRODUCT_NAME, null, null, unitPrice, null, null, null)
        em.persist(product)
        val version =
            ProductVersion.snapshot(
                product = product,
                versionNo = 1,
                nameSnapshot = PRODUCT_NAME,
                priceSnapshot = unitPrice,
                statusSnapshot = SaleStatus.ON_SALE,
                thumbnailKeySnapshot = "thumb-${sig++}",
                changedBy = VersionChangedBy.SELLER,
            )
        em.persist(version)
        val combo = OptionCombination.create(product, OPTION_NAME, 0L, 100, "sig-${sig++}")
        em.persist(combo)
        em.flush()
        return OrderItem.of(
            productId = product.id,
            optionCombinationId = combo.id,
            optionNameSnapshot = OPTION_NAME,
            quantity = 1,
            unitPriceSnapshot = unitPrice,
            discountAllocatedAmount = 0L,
            productVersionId = version.id,
        )
    }

    /** 전이 API를 거치지 않고 상태를 심는다 — 주문 전이 규칙은 이 테스트의 대상이 아니다. */
    private fun forceOrderStatus(
        order: Order,
        status: OrderStatus,
    ) {
        Order::class.java
            .getDeclaredField("status")
            .apply { isAccessible = true }
            .set(order, status)
    }

    private companion object {
        const val PRODUCT_NAME = "멍냥 사료"
        const val OPTION_NAME = "기본 / 1개"
        const val COURIER_CODE = "AECHAK"
    }
}
