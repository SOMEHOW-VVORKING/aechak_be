package com.aechak.seller.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.pii.support.PiiStringCodec
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.UserProfile
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.nullValue
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 셀러 주문 목록·상세 조회 통합. 깨지면 셀러센터 주문 화면의 계약이 깨진 것이다 —
 * 셀러 스코프 격리(타 셀러 주문 비노출), 결제대기 제외, 세그먼트 환산, 집계 금액, PII 평문 반환이 대상이다.
 */
class SellerOrderQueryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var piiStringCodec: PiiStringCodec

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private var buyerUserId = 0L
    private lateinit var token: String

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
        buyerUserId = createBuyer("집사일")
    }

    @Test
    fun `내 주문 목록은 최신순으로 내려간다`() {
        val older = seedOrder(status = OrderStatus.PAID)
        val newer = seedOrder(status = OrderStatus.SHIPPING)

        getOrders()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(newer.publicId))
            .andExpect(jsonPath("$.data.orders[1].orderId").value(older.publicId))
    }

    @Test
    fun `결제대기 주문은 전체 조회에도 나오지 않는다`() {
        seedOrder(status = OrderStatus.PENDING_PAYMENT)
        val paid = seedOrder(status = OrderStatus.PAID)

        getOrders()
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.orders.length()").value(1))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(paid.publicId))
    }

    @Test
    fun `타 셀러 주문은 목록에 섞이지 않는다`() {
        val mine = seedOrder(status = OrderStatus.PAID)
        val otherSeller = createActiveUser().also { openSeller(it) }
        seedOrder(status = OrderStatus.PAID, sellerId = otherSeller)

        getOrders()
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(mine.publicId))
    }

    @Test
    fun `신규 주문 세그먼트는 결제완료만 담는다`() {
        val paid = seedOrder(status = OrderStatus.PAID)
        seedOrder(status = OrderStatus.PREPARING)

        getOrders("status=new")
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(paid.publicId))
            .andExpect(jsonPath("$.data.orders[0].status").value("PAID"))
    }

    @Test
    fun `발송 대기 세그먼트는 준비중과 발송대기를 함께 담는다`() {
        seedOrder(status = OrderStatus.PREPARING)
        seedOrder(status = OrderStatus.DISPATCH_PENDING)
        seedOrder(status = OrderStatus.PAID)

        getOrders("status=awaiting-dispatch")
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.orders[*].status").value(containsInAnyOrder("DISPATCH_PENDING", "PREPARING")))
    }

    @Test
    fun `배송중 세그먼트는 배송중만 담는다`() {
        val shipping = seedOrder(status = OrderStatus.SHIPPING)
        seedOrder(status = OrderStatus.DELIVERED)

        getOrders("status=shipping")
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(shipping.publicId))
    }

    @Test
    fun `배송완료 세그먼트는 배송완료와 구매확정을 함께 담는다`() {
        seedOrder(status = OrderStatus.DELIVERED)
        seedOrder(status = OrderStatus.PURCHASE_CONFIRMED)
        seedOrder(status = OrderStatus.SHIPPING)

        getOrders("status=delivered")
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.orders[*].status").value(containsInAnyOrder("DELIVERED", "PURCHASE_CONFIRMED")))
    }

    @Test
    fun `취소 세그먼트는 취소만 담는다`() {
        val cancelled = seedOrder(status = OrderStatus.CANCELLED)
        seedOrder(status = OrderStatus.PAID)

        getOrders("status=cancelled")
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(cancelled.publicId))
    }

    @Test
    fun `모르는 세그먼트는 400과 90001이다`() {
        getOrders("status=dispute")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `키워드는 주문번호와 상품명으로 검색한다`() {
        val feed = seedOrder(status = OrderStatus.PAID, productName = "동결건조 간식 100g")
        val toy = seedOrder(status = OrderStatus.PAID, productName = "노즈워크 장난감")

        getOrders("keyword=간식")
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(feed.publicId))

        getOrders("keyword=${toy.publicId}")
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(toy.publicId))
    }

    @Test
    fun `키워드는 옵션명으로도 검색한다`() {
        val large = seedOrder(status = OrderStatus.PAID, optionName = "대형견용 / 1kg")
        seedOrder(status = OrderStatus.PAID, optionName = "소형견용 / 100g")

        getOrders("keyword=대형견용")
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(large.publicId))
    }

    @Test
    fun `주문일 범위는 상한 날짜를 통째로 포함한다`() {
        val onBoundary = seedOrder(status = OrderStatus.PAID, orderedAt = LocalDate.of(2026, 10, 18).atTime(23, 59, 59))
        seedOrder(status = OrderStatus.PAID, orderedAt = LocalDate.of(2026, 10, 19).atStartOfDay())
        seedOrder(status = OrderStatus.PAID, orderedAt = LocalDate.of(2026, 10, 17).atTime(23, 59, 59))

        getOrders("orderedFrom=2026-10-18&orderedTo=2026-10-18")
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(onBoundary.publicId))
    }

    @Test
    fun `역전된 주문일 범위는 400과 90001이다`() {
        getOrders("orderedFrom=2026-10-18&orderedTo=2026-10-17")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `페이지 경계에서 다음 페이지 여부와 행이 갈린다`() {
        val orders = (1..3).map { seedOrder(status = OrderStatus.PAID) }

        getOrders("page=0&size=2")
            .andExpect(jsonPath("$.data.totalCount").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.orders.length()").value(2))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(orders[2].publicId))

        getOrders("page=1&size=2")
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.orders.length()").value(1))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(orders[0].publicId))
    }

    @Test
    fun `마지막 페이지를 넘어선 요청은 빈 목록에 다음 페이지도 없다`() {
        seedOrder(status = OrderStatus.PAID)

        getOrders("page=5&size=20")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orders.length()").value(0))
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `size 상한을 넘기면 400과 90001이다`() {
        getOrders("size=101")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `목록 행의 금액은 이 셀러 몫 집계다`() {
        seedOrder(
            status = OrderStatus.PAID,
            items = listOf(SeedItem(optionName = "100g / 단품", quantity = 2, unitPrice = 24_000L)),
            shippingFee = 3_000L,
        )

        getOrders()
            .andExpect(jsonPath("$.data.orders[0].itemCount").value(1))
            .andExpect(jsonPath("$.data.orders[0].totalProductAmount").value(48_000))
            .andExpect(jsonPath("$.data.orders[0].sellerShippingFee").value(3_000))
            .andExpect(jsonPath("$.data.orders[0].allocatedCouponDiscount").value(0))
            .andExpect(jsonPath("$.data.orders[0].payableAmount").value(51_000))
    }

    @Test
    fun `품목이 여러 개인 주문도 목록에 한 번만 나온다`() {
        seedOrder(
            status = OrderStatus.PAID,
            items =
                listOf(
                    SeedItem(optionName = "100g / 단품", quantity = 1, unitPrice = 24_000L),
                    SeedItem(optionName = "200g / 단품", quantity = 1, unitPrice = 30_000L),
                ),
        )

        // 품목 조인이 들어오면 행이 품목 수만큼 불어난다 — 키워드가 두 품목에 걸리는 경우까지 함께 본다
        getOrders("keyword=단품")
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.orders.length()").value(1))
            .andExpect(jsonPath("$.data.orders[0].itemCount").value(2))
            .andExpect(jsonPath("$.data.orders[0].totalProductAmount").value(54_000))
    }

    @Test
    fun `수령인명과 연락처는 복호된 평문으로 내려간다`() {
        seedOrder(status = OrderStatus.PAID, receiverName = "홍길동", contactNumber = "010-1234-5678")

        getOrders()
            .andExpect(jsonPath("$.data.orders[0].recipientName").value("홍길동"))
            .andExpect(jsonPath("$.data.orders[0].contactNumber").value("010-1234-5678"))
            .andExpect(jsonPath("$.data.orders[0].buyerNickname").value("집사일"))
            .andExpect(jsonPath("$.data.orders[0].zipCode").value("06236"))
            .andExpect(jsonPath("$.data.orders[0].baseAddress").value("서울 강남구 테헤란로 1"))
            .andExpect(jsonPath("$.data.orders[0].detailAddress").value("101동 1203호"))
            .andExpect(jsonPath("$.data.orders[0].deliveryMemo").value("문 앞"))
    }

    @Test
    fun `배송 정보가 없는 주문의 배송과 클레임은 null이다`() {
        seedOrder(status = OrderStatus.PAID)

        getOrders()
            .andExpect(jsonPath("$.data.orders[0].shipment").value(nullValue()))
            .andExpect(jsonPath("$.data.orders[0].activeClaim").value(nullValue()))
            .andExpect(jsonPath("$.data.orders[0].purchaseConfirmDueAt").value(nullValue()))
    }

    @Test
    fun `송장이 등록된 주문은 배송 정보를 함께 내린다`() {
        val order = seedOrder(status = OrderStatus.SHIPPING)
        seedShipment(order.id, trackingNumber = "123456789012")

        getOrders()
            .andExpect(jsonPath("$.data.orders[0].shipment.courierCode").value("CJ"))
            .andExpect(jsonPath("$.data.orders[0].shipment.courierName").value("CJ대한통운"))
            .andExpect(jsonPath("$.data.orders[0].shipment.trackingNumber").value("123456789012"))
            .andExpect(jsonPath("$.data.orders[0].shipment.dispatchedAt").exists())
            .andExpect(jsonPath("$.data.orders[0].shipment.deliveredAt").value(nullValue()))
    }

    @Test
    fun `재발송이 붙은 주문은 최신 배송을 내린다`() {
        val order = seedOrder(status = OrderStatus.SHIPPING)
        seedShipment(order.id, trackingNumber = "111111111111")
        seedShipment(order.id, trackingNumber = "222222222222", shipmentType = ShipmentType.EXCHANGE_RESHIP)

        getOrders()
            .andExpect(jsonPath("$.data.orders[0].shipment.trackingNumber").value("222222222222"))
    }

    @Test
    fun `진행 중인 클레임은 목록에 함께 내린다`() {
        val order = seedOrder(status = OrderStatus.PREPARING)
        val claimPublicId = seedClaim(order.id, ClaimType.CANCEL)

        getOrders()
            .andExpect(jsonPath("$.data.orders[0].activeClaim.claimId").value(claimPublicId))
            .andExpect(jsonPath("$.data.orders[0].activeClaim.claimType").value("CANCEL"))
            .andExpect(jsonPath("$.data.orders[0].activeClaim.status").value("CLAIM_REQUESTED"))
    }

    @Test
    fun `거부되거나 철회된 클레임은 진행 중으로 보지 않는다`() {
        val rejected = seedOrder(status = OrderStatus.PREPARING)
        seedClaim(rejected.id, ClaimType.CANCEL, status = ClaimStatus.REJECTED)
        val withdrawn = seedOrder(status = OrderStatus.PREPARING)
        seedClaim(withdrawn.id, ClaimType.RETURN, status = ClaimStatus.WITHDRAWN)

        getOrders()
            .andExpect(jsonPath("$.data.orders[0].activeClaim").value(nullValue()))
            .andExpect(jsonPath("$.data.orders[1].activeClaim").value(nullValue()))
    }

    @Test
    fun `종결 시각이 찍힌 클레임은 승인 상태여도 진행 중으로 보지 않는다`() {
        val order = seedOrder(status = OrderStatus.DELIVERED)
        seedClaim(order.id, ClaimType.RETURN, status = ClaimStatus.APPROVED, resolvedAt = LocalDateTime.now())

        getOrders()
            .andExpect(jsonPath("$.data.orders[0].activeClaim").value(nullValue()))
    }

    @Test
    fun `승인됐지만 처리 중인 클레임은 진행 중이다`() {
        val order = seedOrder(status = OrderStatus.DELIVERED)
        val claimPublicId = seedClaim(order.id, ClaimType.RETURN, status = ClaimStatus.APPROVED)

        getOrders()
            .andExpect(jsonPath("$.data.orders[0].activeClaim.claimId").value(claimPublicId))
            .andExpect(jsonPath("$.data.orders[0].activeClaim.status").value("APPROVED"))
    }

    @Test
    fun `상세는 품목과 배송 클레임을 함께 내린다`() {
        val order =
            seedOrder(
                status = OrderStatus.SHIPPING,
                productName = "동결건조 간식 100g",
                items =
                    listOf(
                        SeedItem(optionName = "100g / 단품", quantity = 2, unitPrice = 24_000L),
                        SeedItem(optionName = "200g / 단품", quantity = 1, unitPrice = 30_000L),
                    ),
                shippingFee = 3_000L,
            )
        seedShipment(order.id, trackingNumber = "123456789012")
        val claimPublicId = seedClaim(order.id, ClaimType.RETURN)

        mockMvc
            .perform(detailRequest(token, order.publicId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orderId").value(order.publicId))
            .andExpect(jsonPath("$.data.orderGroupId").value(order.groupPublicId))
            .andExpect(jsonPath("$.data.status").value("SHIPPING"))
            .andExpect(jsonPath("$.data.itemCount").value(2))
            .andExpect(jsonPath("$.data.totalProductAmount").value(78_000))
            .andExpect(jsonPath("$.data.payableAmount").value(81_000))
            .andExpect(jsonPath("$.data.recipientName").value("홍길동"))
            .andExpect(jsonPath("$.data.shipment.trackingNumber").value("123456789012"))
            .andExpect(jsonPath("$.data.activeClaim.claimId").value(claimPublicId))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].productId").value(order.productPublicId))
            .andExpect(jsonPath("$.data.items[0].productName").value("동결건조 간식 100g"))
            .andExpect(jsonPath("$.data.items[0].thumbnailUrl").value("https://fake-cdn.local/${order.thumbnailKey}"))
            .andExpect(jsonPath("$.data.items[0].optionName").value("100g / 단품"))
            .andExpect(jsonPath("$.data.items[0].quantity").value(2))
            .andExpect(jsonPath("$.data.items[0].unitPrice").value(24_000))
            .andExpect(jsonPath("$.data.items[0].discountAllocatedAmount").value(0))
            .andExpect(jsonPath("$.data.items[0].itemStatus").value("ORDERED"))
            .andExpect(jsonPath("$.data.items[1].optionName").value("200g / 단품"))
    }

    @Test
    fun `없는 주문의 상세는 404와 50000이다`() {
        seedOrder(status = OrderStatus.PAID)

        mockMvc
            .perform(detailRequest(token, "01JZZZZZZZZZZZZZZZZZZZZZZZ"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `타 셀러 주문의 상세는 404와 50000이다`() {
        val order = seedOrder(status = OrderStatus.PAID)
        val otherSeller = createActiveUser().also { openSeller(it) }

        mockMvc
            .perform(detailRequest(mintAccessToken(otherSeller), order.publicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `결제대기 주문의 상세는 404와 50000이다`() {
        val order = seedOrder(status = OrderStatus.PENDING_PAYMENT)

        mockMvc
            .perform(detailRequest(token, order.publicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `휴점 셀러도 자기 주문을 조회한다`() {
        seedOrder(status = OrderStatus.PAID)
        changeSellerStatus(sellerUserId, SellerStatus.PAUSED)

        getOrders().andExpect(status().isOk).andExpect(jsonPath("$.data.totalCount").value(1))
    }

    @Test
    fun `정지된 셀러의 주문 조회는 403과 50003이다`() {
        val order = seedOrder(status = OrderStatus.PAID)
        changeSellerStatus(sellerUserId, SellerStatus.SUSPENDED)

        getOrders()
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(50003))

        mockMvc
            .perform(detailRequest(token, order.publicId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(50003))
    }

    @Test
    fun `셀러가 아닌 계정의 주문 조회는 403과 50003이다`() {
        seedOrder(status = OrderStatus.PAID)
        val notSeller = createActiveUser()

        mockMvc
            .perform(get("/api/v1/sellers/me/orders").header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(notSeller)}"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(50003))
    }

    @Test
    fun `미인증 주문 조회는 401과 20004다`() {
        mockMvc
            .perform(get("/api/v1/sellers/me/orders"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    /**
     * 조회 쿼리 수가 주문 수와 무관함을 고정한다 — 배송·클레임·구매자 닉네임을 행마다 조회하면 깨진다.
     * 전제 둘. 목록이 페이지 한 번 + 카운트 한 번이고, 조립 재료는 주문 묶음 단위 IN 조회다.
     * 통계는 프로퍼티가 아니라 런타임에 켠다 — properties를 바꾸면 컨텍스트가 갈라져 공유 MySQL 컨테이너가 조기 종료될 수 있다.
     */
    @Nested
    inner class ListQueryCount {
        private lateinit var statistics: Statistics

        @BeforeEach
        fun enableStatistics() {
            statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
            statistics.isStatisticsEnabled = true
        }

        @Test
        fun `주문이 늘어도 목록 조회 쿼리 수는 그대로다`() {
            seedOrderWithBundle(index = 1)
            val withOneOrder = countStatementsOnList(expectedOrders = 1)

            (2..5).forEach { seedOrderWithBundle(index = it) }
            val withFiveOrders = countStatementsOnList(expectedOrders = 5)

            assertEquals(
                withOneOrder,
                withFiveOrders,
                "주문마다 배송·클레임·닉네임을 단건 조회하면 주문 수만큼 쿼리가 늘어난다 (1건=$withOneOrder, 5건=$withFiveOrders)",
            )
        }

        @Test
        fun `목록 조회는 요청당 한 자릿수 쿼리로 끝난다`() {
            (1..5).forEach { seedOrderWithBundle(index = it) }

            val statements = countStatementsOnList(expectedOrders = 5)

            assertTrue(statements in 1..9, "목록 조회 쿼리 수가 예상 범위를 벗어났다: $statements")
        }

        /** 구매자를 주문마다 다르게 두고 배송·클레임까지 붙인다 — 조립 재료별 N+1이 각각 드러나야 한다. */
        private fun seedOrderWithBundle(index: Int) {
            val order = seedOrder(status = OrderStatus.DELIVERED, buyerId = createBuyer("집사$index"))
            seedShipment(order.id, trackingNumber = "10000000000$index")
            seedClaim(order.id, ClaimType.RETURN)
        }

        /** 쿼리 수만 세면 조립이 터져 응답이 비어도 통과한다 — 응답 성공과 행 수를 함께 고정한다. */
        private fun countStatementsOnList(expectedOrders: Int): Long {
            statistics.clear()
            getOrders()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.orders.length()").value(expectedOrders))
                .andExpect(jsonPath("$.data.orders[0].shipment.trackingNumber").exists())
                .andExpect(jsonPath("$.data.orders[0].activeClaim.claimId").exists())
                .andExpect(jsonPath("$.data.orders[0].buyerNickname").exists())
            return statistics.prepareStatementCount
        }
    }

    // ---------- 헬퍼 ----------

    private fun getOrders(query: String = ""): ResultActions =
        mockMvc.perform(
            get("/api/v1/sellers/me/orders" + if (query.isEmpty()) "" else "?$query")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
        )

    private fun detailRequest(
        token: String,
        orderPublicId: String,
    ) = get("/api/v1/sellers/me/orders/$orderPublicId").header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 0L)) }
    }

    private fun changeSellerStatus(
        userId: Long,
        status: SellerStatus,
    ) {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :status where s.userId = :userId")
                .setParameter("status", status)
                .setParameter("userId", userId)
                .executeUpdate()
        }
    }

    private fun createBuyer(nickname: String): Long {
        val userId = createActiveUser()
        tx.execute {
            val user = em.find(User::class.java, userId)
            em.persist(UserProfile.of(user, nickname))
        }
        return userId
    }

    /** 주문그룹 1건 + 셀러 주문 1건 + 상품·버전 스냅샷을 심는다. */
    private fun seedOrder(
        status: OrderStatus,
        sellerId: Long = sellerUserId,
        productName: String = "동결건조 간식 100g",
        optionName: String = "100g / 단품",
        items: List<SeedItem> = listOf(SeedItem(optionName, quantity = 1, unitPrice = 25_000L)),
        shippingFee: Long = 0L,
        receiverName: String = "홍길동",
        contactNumber: String = "010-1234-5678",
        orderedAt: LocalDateTime? = null,
        buyerId: Long = buyerUserId,
    ): SeedOrder {
        val seed =
            tx.execute {
                val category = Category.create(null, 1, "강아지$productName${System.nanoTime()}", null, 1)
                em.persist(category)
                val product =
                    Product.register(
                        category,
                        sellerId,
                        productName,
                        null,
                        THUMBNAIL_KEY,
                        25_000L,
                        null,
                        null,
                        null,
                    )
                em.persist(product)
                val version = ProductVersion.create(product, THUMBNAIL_KEY)
                em.persist(version)

                val productAmount = items.sumOf { it.unitPrice * it.quantity }
                val group =
                    OrderGroup.create(
                        buyerId = buyerId,
                        deliveryAddressId = 0L,
                        deliveryAddress =
                            DeliveryAddressSnapshot(
                                receiverNameEnc = piiStringCodec.encrypt(receiverName),
                                contactNumberEnc = piiStringCodec.encrypt(contactNumber),
                                zipCode = "06236",
                                baseAddress = "서울 강남구 테헤란로 1",
                                detailAddress = "101동 1203호",
                                deliveryMemo = "문 앞",
                            ),
                        usedPoint = 0L,
                        totalProductAmount = productAmount,
                        totalShippingFee = shippingFee,
                        idempotencyKey = "query-${status.name}-${System.nanoTime()}",
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
                        baseShippingFeeSnapshot = null,
                        freeShippingThresholdSnapshot = null,
                        items =
                            items.mapIndexed { index, item ->
                                OrderItem.of(
                                    productId = product.id,
                                    optionCombinationId = index + 1L,
                                    optionNameSnapshot = item.optionName,
                                    quantity = item.quantity,
                                    unitPriceSnapshot = item.unitPrice,
                                    discountAllocatedAmount = 0L,
                                    productVersionId = version.id,
                                )
                            },
                    )
                em.persist(order)
                em.flush()
                em
                    .createQuery("update Order o set o.status = :status where o.id = :id")
                    .setParameter("status", status)
                    .setParameter("id", order.id)
                    .executeUpdate()
                SeedOrder(order.id, order.publicId, group.id, group.publicId, product.publicId, THUMBNAIL_KEY)
            }!!
        orderedAt?.let { overrideOrderedAt(seed.groupId, it) }
        return seed
    }

    /** 주문일은 생성 시각 콜백이 채우므로 네이티브 UPDATE로 바꾼다 — created_at은 JPA 매핑이 갱신을 막는다. */
    private fun overrideOrderedAt(
        groupId: Long,
        orderedAt: LocalDateTime,
    ) {
        tx.execute {
            em
                .createNativeQuery("update order_groups set created_at = ? where id = ?")
                .setParameter(1, orderedAt)
                .setParameter(2, groupId)
                .executeUpdate()
        }
    }

    private fun seedShipment(
        orderId: Long,
        trackingNumber: String,
        shipmentType: ShipmentType = ShipmentType.INITIAL,
    ) {
        tx.execute {
            val courier =
                em.find(CourierMaster::class.java, COURIER_CODE)
                    ?: CourierMaster.register(COURIER_CODE, "CJ대한통운").also { em.persist(it) }
            val order = em.find(Order::class.java, orderId)
            em.persist(Shipment.dispatch(courier, order, trackingNumber, shipmentType))
        }
    }

    /**
     * 반품·교환은 요청 경로가 아직 열리지 않았고 취소도 발송 전 상태에서만 받는다 —
     * 결제완료로 잠시 올려 취소 클레임을 만든 뒤 유형·상태를 쿼리로 옮긴다. 주문 상태는 원래 값으로 되돌린다.
     */
    private fun seedClaim(
        orderId: Long,
        claimType: ClaimType,
        status: ClaimStatus = ClaimStatus.CLAIM_REQUESTED,
        resolvedAt: LocalDateTime? = null,
    ): String =
        tx.execute {
            val order = em.find(Order::class.java, orderId)
            val originalStatus = order.status
            forceOrderStatus(order, OrderStatus.PAID)
            val claim =
                Claim.request(
                    order = order,
                    claimType = ClaimType.CANCEL,
                    reasonCode = "CHANGE_OF_MIND",
                    reasonDetail = null,
                    items = listOf(ClaimItem.of(order.items.first(), 1, 25_000L)),
                    autoApproveDueAt = LocalDateTime.now().plusDays(2),
                )
            forceOrderStatus(order, originalStatus)
            em.persist(claim)
            em.flush()
            em
                .createQuery(
                    "update Claim c set c.claimType = :claimType, c.status = :status, c.resolvedAt = :resolvedAt where c.id = :id",
                ).setParameter("claimType", claimType)
                .setParameter("status", status)
                .setParameter("resolvedAt", resolvedAt)
                .setParameter("id", claim.id)
                .executeUpdate()
            claim.publicId
        }!!

    private fun forceOrderStatus(
        order: Order,
        status: OrderStatus,
    ) {
        Order::class.java
            .getDeclaredField("status")
            .apply { isAccessible = true }
            .set(order, status)
    }

    private data class SeedItem(
        val optionName: String,
        val quantity: Int,
        val unitPrice: Long,
    )

    private data class SeedOrder(
        val id: Long,
        val publicId: String,
        val groupId: Long,
        val groupPublicId: String,
        val productPublicId: String,
        val thumbnailKey: String,
    )

    private companion object {
        private const val THUMBNAIL_KEY = "media/product/seed/rep.webp"
        private const val COURIER_CODE = "CJ"
    }
}
