package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.pii.support.PiiStringCodec
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.repository.PaymentRepository
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.seller.seller.Seller
import com.jayway.jsonpath.JsonPath
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 주문 목록·상세 조회 API 통합. HTTP 경계부터 실 MySQL까지 태워 결제대기 제외·타인 은닉·커서 경계 계약을 고정함.
 * 주문을 결제 전에 미리 만드는 모델이라 결제대기 행이 테이블에 섞여 있고, 그 행이 어느 응답으로도 새지 않는 것이 이 API의 핵심 계약임.
 */
class OrderQueryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var piiStringCodec: PiiStringCodec

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    private var sig = 0

    private fun seedCombo(
        sellerUserId: Long,
        storeName: String,
        price: Long,
        productName: String,
    ): Pair<Long, Long> =
        tx.execute {
            val tag = "s${sig++}"
            em.persist(Seller.open(sellerUserId, storeName, 3000L))
            val category = Category.create(null, 1, "카테고리-$tag", null, 1)
            em.persist(category)
            val product = Product.register(category, sellerUserId, productName, null, null, price, null, null, null)
            em.persist(product)
            val version =
                ProductVersion.snapshot(
                    product = product,
                    versionNo = 1,
                    nameSnapshot = productName,
                    priceSnapshot = price,
                    statusSnapshot = SaleStatus.ON_SALE,
                    thumbnailKeySnapshot = "thumb-$tag",
                    changedBy = VersionChangedBy.SELLER,
                )
            em.persist(version)
            val combo = OptionCombination.create(product, "용량 5L / 화이트", 0L, 100, "sig-$tag")
            em.persist(combo)
            em.flush()
            combo.id to version.id
        }!!

    /** 주문그룹 하나와 그 아래 셀러 주문들을 심고 (그룹 publicId, 주문 publicId 목록)을 돌려줌. */
    private fun seedOrderGroup(
        buyerId: Long,
        sellers: List<SellerOrderSpec>,
        groupStatus: com.aechak.domain.order.group.enums.OrderGroupStatus,
        receiverName: String = RECEIVER_NAME,
        contactNumber: String = CONTACT_NUMBER,
    ): Pair<String, List<String>> =
        tx.execute {
            val productAmount = sellers.sumOf { it.unitPrice * it.quantity }
            val shippingFee = sellers.sumOf { it.shippingFee }
            val group =
                OrderGroup.create(
                    buyerId = buyerId,
                    deliveryAddressId = 1L,
                    deliveryAddress =
                        DeliveryAddressSnapshot(
                            receiverNameEnc = piiStringCodec.encrypt(receiverName),
                            contactNumberEnc = piiStringCodec.encrypt(contactNumber),
                            zipCode = "12345",
                            baseAddress = "서울시 애착구 멍냥로 1",
                            detailAddress = "101동",
                            deliveryMemo = "문 앞에 놓아주세요",
                        ),
                    usedPoint = 0,
                    totalProductAmount = productAmount,
                    totalShippingFee = shippingFee,
                    idempotencyKey = "key-$buyerId-${sig++}",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )
            if (groupStatus != com.aechak.domain.order.group.enums.OrderGroupStatus.PENDING_PAYMENT) {
                forceGroupStatus(group, groupStatus)
            }
            em.persist(group)
            val orderPublicIds =
                sellers.map { spec ->
                    val order =
                        Order.create(
                            orderGroup = group,
                            sellerId = spec.sellerId,
                            sellerNameSnapshot = spec.sellerName,
                            allocatedCouponDiscount = 0,
                            sellerShippingFee = spec.shippingFee,
                            baseShippingFeeSnapshot = null,
                            freeShippingThresholdSnapshot = null,
                            items =
                                listOf(
                                    OrderItem.of(
                                        productId = spec.productId,
                                        optionCombinationId = spec.optionCombinationId,
                                        optionNameSnapshot = "용량 5L / 화이트",
                                        quantity = spec.quantity,
                                        unitPriceSnapshot = spec.unitPrice,
                                        discountAllocatedAmount = 0,
                                        productVersionId = spec.productVersionId,
                                    ),
                                ),
                        )
                    forceOrderStatus(order, spec.status)
                    spec.autoConfirmDueAt?.let { forceAutoConfirmDueAt(order, it) }
                    em.persist(order)
                    order.publicId
                }
            em.flush()
            group.publicId to orderPublicIds
        }!!

    /** 상태 전이 API가 아직 없어 리플렉션으로 심음. 전이 규칙 자체는 이 테스트의 대상이 아님. */
    private fun forceOrderStatus(
        order: Order,
        status: OrderStatus,
    ) {
        val field = Order::class.java.getDeclaredField("status")
        field.isAccessible = true
        field.set(order, status)
    }

    private fun forceAutoConfirmDueAt(
        order: Order,
        dueAt: LocalDateTime,
    ) {
        val field = Order::class.java.getDeclaredField("autoConfirmDueAt")
        field.isAccessible = true
        field.set(order, dueAt)
    }

    private fun forceGroupStatus(
        group: OrderGroup,
        status: com.aechak.domain.order.group.enums.OrderGroupStatus,
    ) {
        val field = OrderGroup::class.java.getDeclaredField("status")
        field.isAccessible = true
        field.set(group, status)
    }

    private data class SellerOrderSpec(
        val sellerId: Long,
        val sellerName: String,
        val productId: Long,
        val optionCombinationId: Long,
        val productVersionId: Long,
        val unitPrice: Long,
        val quantity: Int,
        val shippingFee: Long,
        val status: OrderStatus,
        val autoConfirmDueAt: LocalDateTime? = null,
    )

    private fun specOf(
        sellerId: Long,
        sellerName: String,
        productName: String,
        price: Long,
        status: OrderStatus,
        quantity: Int = 1,
        autoConfirmDueAt: LocalDateTime? = null,
    ): SellerOrderSpec {
        val (comboId, versionId) = seedCombo(sellerId, sellerName, price, productName)
        val productId =
            em
                .createQuery("select oc.product.id from OptionCombination oc where oc.id = :id", java.lang.Long::class.java)
                .setParameter("id", comboId)
                .singleResult
                .toLong()
        return SellerOrderSpec(sellerId, sellerName, productId, comboId, versionId, price, quantity, 3000L, status, autoConfirmDueAt)
    }

    /** 택배사 마스터를 없으면 심고 운송장을 하나 등록한다. */
    private fun seedShipment(
        orderPublicId: String,
        courierCode: String,
        courierName: String,
        trackingNumber: String,
        shipmentType: ShipmentType = ShipmentType.INITIAL,
    ) {
        tx.executeWithoutResult {
            val courier =
                em.find(CourierMaster::class.java, courierCode)
                    ?: CourierMaster.register(courierCode, courierName).also { em.persist(it) }
            val order = loadOrder(orderPublicId)
            em.persist(Shipment.dispatch(courier, order, trackingNumber, shipmentType))
            em.flush()
        }
    }

    /** 취소 클레임을 요청 상태로 심고 그 publicId를 돌려준다. itemIndex를 주면 그 품목 한 건만 대상으로 잡는다. */
    private fun seedRequestedClaim(
        orderPublicId: String,
        itemIndex: Int? = null,
    ): String =
        tx.execute {
            val order = loadOrder(orderPublicId)
            val targets = if (itemIndex == null) order.items else listOf(order.items[itemIndex])
            val claim =
                Claim.request(
                    order = order,
                    claimType = ClaimType.CANCEL,
                    reasonCode = "CHANGE_MIND",
                    reasonDetail = "단순 변심",
                    items = targets.map { ClaimItem.of(it, it.quantity, it.unitPriceSnapshot * it.quantity) },
                    autoApproveDueAt = LocalDateTime.now().plusDays(2),
                )
            em.persist(claim)
            em.flush()
            claim.publicId
        }!!

    private fun seedApprovedPayment(
        orderGroupPublicId: String,
        method: PaymentMethod,
        paidAmount: Long,
    ) {
        tx.executeWithoutResult {
            val groupId =
                em
                    .createQuery("select g.id from OrderGroup g where g.publicId = :pid", java.lang.Long::class.java)
                    .setParameter("pid", orderGroupPublicId)
                    .singleResult
                    .toLong()
            val prepared = Payment.prepare(groupId, "pay-${sig++}", method, paidAmount)
            paymentRepository.save(prepared.approve("tx-${sig++}", paidAmount))
        }
    }

    /** 셀러 주문 한 건이 품목 N개를 들도록 심고 그 주문 publicId를 돌려준다. */
    private fun seedOrderWithItemCount(
        buyerId: Long,
        itemCount: Int,
        status: OrderStatus = OrderStatus.SHIPPING,
    ): String =
        tx.execute {
            val tag = "m${sig++}"
            val sellerId = 300L + sig
            em.persist(Seller.open(sellerId, "묶음가게-$tag", 3000L))
            val category = Category.create(null, 1, "카테고리-$tag", null, 1)
            em.persist(category)
            val product = Product.register(category, sellerId, "상품-$tag", null, null, 1000L, null, null, null)
            em.persist(product)
            val version =
                ProductVersion.snapshot(
                    product = product,
                    versionNo = 1,
                    nameSnapshot = "상품-$tag",
                    priceSnapshot = 1000L,
                    statusSnapshot = SaleStatus.ON_SALE,
                    thumbnailKeySnapshot = "thumb-$tag",
                    changedBy = VersionChangedBy.SELLER,
                )
            em.persist(version)
            val combos =
                (1..itemCount).map { i ->
                    OptionCombination.create(product, "옵션 $i", 0L, 100, "sig-$tag-$i").also { em.persist(it) }
                }
            em.flush()
            val group =
                OrderGroup.create(
                    buyerId = buyerId,
                    deliveryAddressId = 1L,
                    deliveryAddress =
                        DeliveryAddressSnapshot(
                            receiverNameEnc = piiStringCodec.encrypt(RECEIVER_NAME),
                            contactNumberEnc = piiStringCodec.encrypt(CONTACT_NUMBER),
                            zipCode = "12345",
                            baseAddress = "서울시 애착구 멍냥로 1",
                            detailAddress = "101동",
                            deliveryMemo = null,
                        ),
                    usedPoint = 0,
                    totalProductAmount = 1000L * itemCount,
                    totalShippingFee = 3000L,
                    idempotencyKey = "bundle-$tag",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )
            forceGroupStatus(group, OrderGroupStatus.PAID)
            em.persist(group)
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = sellerId,
                    sellerNameSnapshot = "묶음가게-$tag",
                    allocatedCouponDiscount = 0,
                    sellerShippingFee = 3000L,
                    baseShippingFeeSnapshot = null,
                    freeShippingThresholdSnapshot = null,
                    items =
                        combos.map { combo ->
                            OrderItem.of(
                                productId = product.id,
                                optionCombinationId = combo.id,
                                optionNameSnapshot = combo.name,
                                quantity = 1,
                                unitPriceSnapshot = 1000L,
                                discountAllocatedAmount = 0,
                                productVersionId = version.id,
                            )
                        },
                )
            forceOrderStatus(order, status)
            em.persist(order)
            em.flush()
            order.publicId
        }!!

    private fun productPublicIdOf(productId: Long): String =
        em
            .createQuery("select p.publicId from Product p where p.id = :id", String::class.java)
            .setParameter("id", productId)
            .singleResult

    private fun orderItemIdOf(orderPublicId: String): Long = tx.execute { loadOrder(orderPublicId).items.first().id }!!

    /** 배송완료와 마지막 추적 상태를 심는다. 추적 동기화 경로가 없어 상태 문구는 리플렉션으로 넣는다. */
    private fun seedDeliveredTracking(
        orderPublicId: String,
        lastTrackingStatus: String,
        deliveredAt: LocalDateTime,
    ) {
        tx.executeWithoutResult {
            val shipment =
                em
                    .createQuery("select s from Shipment s where s.order.publicId = :pid order by s.id desc", Shipment::class.java)
                    .setParameter("pid", orderPublicId)
                    .resultList
                    .first()
            shipment.markDelivered(deliveredAt)
            val field = Shipment::class.java.getDeclaredField("lastTrackingStatus")
            field.isAccessible = true
            field.set(shipment, lastTrackingStatus)
        }
    }

    private fun loadClaims(): List<Claim> = em.createQuery("select c from Claim c order by c.id asc", Claim::class.java).resultList

    private fun loadOrder(orderPublicId: String): Order =
        em
            .createQuery("select o from Order o where o.publicId = :pid", Order::class.java)
            .setParameter("pid", orderPublicId)
            .singleResult

    private fun detailBody(
        token: String,
        orderPublicId: String,
    ): String =
        mockMvc
            .perform(get("/api/v1/orders/$orderPublicId").bearer(token))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun listBody(
        token: String,
        query: String = "",
    ): String =
        mockMvc
            .perform(get("/api/v1/order-groups$query").bearer(token))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    @Test
    fun `결제대기 주문그룹은 목록에 나오지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        seedOrderGroup(
            buyerId,
            listOf(specOf(71L, "멍멍상회", "결제 안 한 급식기", 10000L, OrderStatus.PENDING_PAYMENT)),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PENDING_PAYMENT,
        )

        mockMvc
            .perform(get("/api/v1/order-groups").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orderGroups").isEmpty)
            .andExpect(jsonPath("$.data.totalCount").value(0))
    }

    @Test
    fun `그룹에 결제대기 주문이 섞여 있어도 그 주문은 목록에 실리지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        seedOrderGroup(
            buyerId,
            listOf(
                specOf(71L, "냥이상점", "고양이 자동 급식기", 64000L, OrderStatus.SHIPPING),
                specOf(72L, "멍멍마트", "결제 안 한 사료", 20000L, OrderStatus.PENDING_PAYMENT),
            ),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
        )

        val body = listBody(token)

        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.orderGroups").size, "배송중 주문이 있으니 그룹 자체는 목록에 남아야 한다")
        val sellers = JsonPath.read<List<String>>(body, "$.data.orderGroups[0].sellerOrders[*].sellerName")
        assertEquals(1, sellers.size, "결제대기 셀러 주문은 카드 안에도 실리면 안 된다: $sellers")
        assertTrue(sellers.contains("냥이상점"), "결제가 끝난 셀러 주문만 남아야 한다: $sellers")
    }

    @Test
    fun `주문그룹 한 행 아래 셀러 주문들이 각자 상태를 달고 나온다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        seedOrderGroup(
            buyerId,
            listOf(
                specOf(71L, "냥이상점", "고양이 자동 급식기", 64000L, OrderStatus.SHIPPING),
                specOf(72L, "멍멍마트", "강아지 사료", 20000L, OrderStatus.PREPARING),
            ),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
        )

        val body = listBody(token)

        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.orderGroups").size, "주문그룹 하나가 한 행이어야 한다")
        val statuses = JsonPath.read<List<String>>(body, "$.data.orderGroups[0].sellerOrders[*].status")
        assertTrue(statuses.containsAll(listOf("SHIPPING", "PREPARING")), "셀러별 상태가 그대로 나와야 한다: $statuses")
        assertEquals(84000, JsonPath.read<Int>(body, "$.data.orderGroups[0].totalProductAmount"), "그룹 상품 금액은 두 셀러 합계여야 한다")
    }

    @Test
    fun `목록의 대표 품목은 셀러가 옵션명을 바꿔도 주문 시점 상품명과 옵션명을 싣는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val spec = specOf(71L, "냥이상점", "고양이 자동 급식기 스마트 5L", 64000L, OrderStatus.DELIVERED)
        seedOrderGroup(buyerId, listOf(spec), com.aechak.domain.order.group.enums.OrderGroupStatus.PAID)
        tx.executeWithoutResult {
            em
                .createQuery("update OptionCombination oc set oc.name = :name where oc.id = :id")
                .setParameter("name", "용량 5L / 아이보리")
                .setParameter("id", spec.optionCombinationId)
                .executeUpdate()
        }

        val body = listBody(token)

        val item = "$.data.orderGroups[0].sellerOrders[0].representativeItem"
        assertEquals("고양이 자동 급식기 스마트 5L", JsonPath.read<String>(body, "$item.productName"), "상품명은 주문 시점 버전 스냅샷이어야 한다")
        assertEquals("용량 5L / 화이트", JsonPath.read<String>(body, "$item.optionName"), "주문 뒤 옵션 개명이 지난 주문 표기에 번지면 안 된다")
        assertEquals(64000, JsonPath.read<Int>(body, "$item.unitPrice"), "단가는 주문 시점 스냅샷이어야 한다")
        assertEquals(1, JsonPath.read<Int>(body, "$.data.orderGroups[0].sellerOrders[0].itemCount"), "품목 종류 수가 맞아야 한다")
    }

    @Test
    fun `남의 주문은 목록에 섞이지 않는다`() {
        val mine = createActiveUser()
        val other = createActiveUser()
        val token = mintAccessToken(mine)
        seedOrderGroup(
            other,
            listOf(specOf(71L, "남의상점", "남의 상품", 5000L, OrderStatus.PAID)),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
        )

        mockMvc
            .perform(get("/api/v1/order-groups").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orderGroups").isEmpty)
    }

    @Test
    fun `진행중 필터는 배송완료와 취소를 걸러낸다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        seedOrderGroup(
            buyerId,
            listOf(specOf(71L, "가게1", "배송중 상품", 1000L, OrderStatus.SHIPPING)),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
        )
        seedOrderGroup(
            buyerId,
            listOf(specOf(72L, "가게2", "배송완료 상품", 1000L, OrderStatus.DELIVERED)),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
        )
        seedOrderGroup(
            buyerId,
            listOf(specOf(73L, "가게3", "취소 상품", 1000L, OrderStatus.CANCELLED)),
            com.aechak.domain.order.group.enums.OrderGroupStatus.CANCELLED,
        )

        val ongoing = listBody(token, "?status=ongoing")
        val completed = listBody(token, "?status=completed")
        val cancelled = listBody(token, "?status=cancelled")
        val all = listBody(token, "?status=all")

        assertEquals(1, JsonPath.read<List<*>>(ongoing, "$.data.orderGroups").size, "진행중은 배송중 하나여야 한다")
        assertEquals(1, JsonPath.read<List<*>>(completed, "$.data.orderGroups").size, "완료는 배송완료 하나여야 한다")
        assertEquals(1, JsonPath.read<List<*>>(cancelled, "$.data.orderGroups").size, "취소는 취소 하나여야 한다")
        assertEquals(3, JsonPath.read<List<*>>(all, "$.data.orderGroups").size, "전체는 셋 다여야 한다")
    }

    @Test
    fun `커서로 끝까지 넘기면 중복 없이 전부 돌고 마지막 페이지에서 멈춘다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        repeat(5) { i ->
            seedOrderGroup(
                buyerId,
                listOf(specOf(80L + i, "가게$i", "상품$i", 1000L, OrderStatus.PAID)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )
        }

        val seen = mutableListOf<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val query = if (cursor == null) "?size=2" else "?size=2&cursor=$cursor"
            val body = listBody(token, query)
            seen += JsonPath.read<List<String>>(body, "$.data.orderGroups[*].orderGroupId")
            cursor = JsonPath.read<String?>(body, "$.data.nextCursor")
            pages++
        } while (cursor != null && pages < 10)

        assertEquals(5, seen.size, "5건을 전부 돌아야 한다")
        assertEquals(5, seen.toSet().size, "중복 없이 돌아야 한다")
        assertEquals(3, pages, "2건씩이면 3페이지여야 한다")
    }

    @Test
    fun `첫 페이지만 totalCount를 싣고 다음 페이지는 비운다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        repeat(3) { i ->
            seedOrderGroup(
                buyerId,
                listOf(specOf(90L + i, "가게$i", "상품$i", 1000L, OrderStatus.PAID)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )
        }

        val first = listBody(token, "?size=2")
        val cursor = JsonPath.read<String>(first, "$.data.nextCursor")
        val second = listBody(token, "?size=2&cursor=$cursor")

        assertEquals(3, JsonPath.read<Int>(first, "$.data.totalCount"), "첫 페이지는 총건수를 싣는다")
        assertNull(JsonPath.parse(second).read<Any?>("$.data.totalCount"), "이후 페이지는 비운다")
    }

    @Test
    fun `주문이 없으면 빈 목록과 hasNext false를 돌려준다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(get("/api/v1/order-groups").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orderGroups").isEmpty)
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
    }

    @Test
    fun `페이지 중간에 필터를 바꾼 커서는 90002로 거절한다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        repeat(3) { i ->
            seedOrderGroup(
                buyerId,
                listOf(specOf(60L + i, "가게$i", "상품$i", 1000L, OrderStatus.PAID)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )
        }
        val cursor = JsonPath.read<String>(listBody(token, "?status=all&size=1"), "$.data.nextCursor")

        mockMvc
            .perform(get("/api/v1/order-groups?status=ongoing&size=1&cursor=$cursor").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `깨진 커서는 90002로 거절한다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(get("/api/v1/order-groups?cursor=%25%25broken").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `알 수 없는 status 값은 90001로 거절한다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(get("/api/v1/order-groups?status=nope").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `size가 상한을 넘으면 90001로 거절한다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(get("/api/v1/order-groups?size=101").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `상세는 주문 시점 품목과 결제 금액을 셀러 배송비와 함께 돌려준다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (groupPublicId, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "고양이 자동 급식기 스마트 5L", 64000L, OrderStatus.SHIPPING)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orderId").value(orderPublicIds.first()))
            .andExpect(jsonPath("$.data.orderGroupId").value(groupPublicId))
            .andExpect(jsonPath("$.data.status").value("SHIPPING"))
            .andExpect(jsonPath("$.data.sellerName").value("냥이상점"))
            .andExpect(jsonPath("$.data.items[0].productName").value("고양이 자동 급식기 스마트 5L"))
            .andExpect(jsonPath("$.data.items[0].optionName").value("용량 5L / 화이트"))
            .andExpect(jsonPath("$.data.items[0].unitPrice").value(64000))
            .andExpect(jsonPath("$.data.payment.sellerShippingFee").value(3000))
            .andExpect(jsonPath("$.data.payment.totalProductAmount").value(64000))
            .andExpect(jsonPath("$.data.payment.finalPaymentAmount").value(67000))
    }

    @Test
    fun `상세 응답에 내부 숫자 id가 실리지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 1000L, OrderStatus.PAID)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )

        val body =
            mockMvc
                .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(26, JsonPath.read<String>(body, "$.data.orderId").length, "주문번호는 ULID 26자여야 한다")
        assertTrue(!body.contains("\"id\""), "내부 id 필드가 응답에 있으면 안 된다: $body")
    }

    @Test
    fun `남의 주문 상세는 50000으로 숨긴다`() {
        val mine = createActiveUser()
        val other = createActiveUser()
        val token = mintAccessToken(mine)
        val (_, orderPublicIds) =
            seedOrderGroup(
                other,
                listOf(specOf(71L, "남의상점", "남의 상품", 5000L, OrderStatus.PAID)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `결제대기 주문 상세는 본인 것이어도 50000으로 숨긴다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "멍멍상회", "결제 안 한 상품", 10000L, OrderStatus.PENDING_PAYMENT)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PENDING_PAYMENT,
            )

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `토큰 없이 목록을 부르면 401이다`() {
        mockMvc
            .perform(get("/api/v1/order-groups"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `토큰 없이 상세를 부르면 401이다`() {
        mockMvc
            .perform(get("/api/v1/orders/01JXFYK3S9GQ4T7VBW2N8DHMCE"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `없는 주문번호는 50000으로 거절한다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(get("/api/v1/orders/01JXFYK3S9GQ4T7VBW2N8DHMCE").bearer(token))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `상세는 리뷰 작성용 품목 식별자와 상품 공개 식별자를 싣는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val spec = specOf(71L, "냥이상점", "고양이 자동 급식기", 64000L, OrderStatus.PURCHASE_CONFIRMED)
        val (_, orderPublicIds) = seedOrderGroup(buyerId, listOf(spec), OrderGroupStatus.PAID)

        val body = detailBody(token, orderPublicIds.first())

        assertEquals(
            productPublicIdOf(spec.productId),
            JsonPath.read<String>(body, "$.data.items[0].productId"),
            "상품 식별자는 상품 상세 API가 받는 공개 id여야 한다 — 내부 숫자 PK를 주면 링크가 깨진다",
        )
        assertEquals(
            orderItemIdOf(orderPublicIds.first()),
            JsonPath.read<Int>(body, "$.data.items[0].orderItemId").toLong(),
            "주문품목 식별자는 리뷰 작성 API가 받는 order_items.id여야 한다",
        )
    }

    @Test
    fun `상세는 주문 당시 배송지 스냅샷을 복호해 싣는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.PAID)),
                OrderGroupStatus.PAID,
            )

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.deliveryAddress.receiverName").value(RECEIVER_NAME))
            .andExpect(jsonPath("$.data.deliveryAddress.contactNumber").value(CONTACT_NUMBER))
            .andExpect(jsonPath("$.data.deliveryAddress.zipCode").value("12345"))
            .andExpect(jsonPath("$.data.deliveryAddress.baseAddress").value("서울시 애착구 멍냥로 1"))
            .andExpect(jsonPath("$.data.deliveryAddress.detailAddress").value("101동"))
            .andExpect(jsonPath("$.data.deliveryAddress.deliveryMemo").value("문 앞에 놓아주세요"))
    }

    @Test
    fun `상세는 결제수단을 싣고 결제 행이 없으면 비운다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (paidGroup, paidOrders) =
            seedOrderGroup(buyerId, listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.PAID)), OrderGroupStatus.PAID)
        val (_, noPaymentOrders) =
            seedOrderGroup(buyerId, listOf(specOf(72L, "멍멍마트", "사료", 20000L, OrderStatus.PAID)), OrderGroupStatus.PAID)
        seedApprovedPayment(paidGroup, PaymentMethod.KAKAO_PAY, 67000L)

        mockMvc
            .perform(get("/api/v1/orders/${paidOrders.first()}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.payment.paymentMethod").value("KAKAO_PAY"))
        assertNull(
            JsonPath.parse(detailBody(token, noPaymentOrders.first())).read<Any?>("$.data.payment.paymentMethod"),
            "결제 행이 없는 주문은 결제수단이 null이어야 한다",
        )
    }

    @Test
    fun `상세는 등록된 운송장과 배송 추적을 싣는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.SHIPPING)),
                OrderGroupStatus.PAID,
            )
        seedShipment(orderPublicIds.first(), "CJ", "CJ대한통운", "123456789012")

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.shipment.courierCode").value("CJ"))
            .andExpect(jsonPath("$.data.shipment.courierName").value("CJ대한통운"))
            .andExpect(jsonPath("$.data.shipment.trackingNumber").value("123456789012"))
            .andExpect(jsonPath("$.data.shipment.dispatchedAt").exists())

        val body = detailBody(token, orderPublicIds.first())
        assertNull(JsonPath.parse(body).read<Any?>("$.data.shipment.deliveredAt"), "배송 완료 전에는 null이어야 한다")
        assertNull(JsonPath.parse(body).read<Any?>("$.data.shipment.lastTrackingStatus"), "추적 동기화 전에는 null이어야 한다")
    }

    @Test
    fun `교환 재발송이 있으면 가장 나중에 등록된 운송장을 싣는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.SHIPPING)),
                OrderGroupStatus.PAID,
            )
        seedShipment(orderPublicIds.first(), "CJ", "CJ대한통운", "111111111111")
        seedShipment(orderPublicIds.first(), "HANJIN", "한진택배", "222222222222", ShipmentType.EXCHANGE_RESHIP)

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.shipment.trackingNumber").value("222222222222"))
            .andExpect(jsonPath("$.data.shipment.courierCode").value("HANJIN"))
    }

    @Test
    fun `배송 정보가 없는 주문 상세는 운송장과 구매확정 기한과 클레임을 비운다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.PAID)),
                OrderGroupStatus.PAID,
            )

        val body = detailBody(token, orderPublicIds.first())

        assertNull(JsonPath.parse(body).read<Any?>("$.data.shipment"), "운송장이 없으면 null이어야 한다")
        assertNull(JsonPath.parse(body).read<Any?>("$.data.purchaseConfirmDueAt"), "배송완료 전에는 구매확정 기한이 null이어야 한다")
        assertNull(JsonPath.parse(body).read<Any?>("$.data.activeClaim"), "클레임이 없으면 null이어야 한다")
    }

    @Test
    fun `배송완료 주문 상세는 구매확정 기한을 싣는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val dueAt = LocalDateTime.now().plusDays(7).withNano(0)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.DELIVERED, autoConfirmDueAt = dueAt)),
                OrderGroupStatus.PAID,
            )

        val body = detailBody(token, orderPublicIds.first())

        assertEquals(
            dueAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
            OffsetDateTime.parse(JsonPath.read<String>(body, "$.data.purchaseConfirmDueAt")),
            "배송완료 건은 자동 구매확정 기한을 오프셋까지 그대로 실어야 한다",
        )
    }

    @Test
    fun `진행 중 클레임이 있으면 상세가 그 클레임을 싣는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.PAID)),
                OrderGroupStatus.PAID,
            )
        val claimPublicId = seedRequestedClaim(orderPublicIds.first())

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.activeClaim.claimPublicId").value(claimPublicId))
            .andExpect(jsonPath("$.data.activeClaim.claimType").value("CANCEL"))
            .andExpect(jsonPath("$.data.activeClaim.status").value("CLAIM_REQUESTED"))
            .andExpect(jsonPath("$.data.activeClaim.refundStatus").value("NONE"))
    }

    @Test
    fun `거부된 클레임은 진행 중으로 싣지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.PAID)),
                OrderGroupStatus.PAID,
            )
        seedRequestedClaim(orderPublicIds.first())
        tx.executeWithoutResult {
            em
                .createQuery("select c from Claim c", Claim::class.java)
                .singleResult
                .rejectBySeller("하자가 확인되지 않습니다")
        }

        assertNull(
            JsonPath.parse(detailBody(token, orderPublicIds.first())).read<Any?>("$.data.activeClaim"),
            "거부된 클레임은 품목을 풀어주므로 진행 중으로 실리면 안 된다",
        )
    }

    @Test
    fun `남의 주문 상세는 배송지 스냅샷도 내주지 않는다`() {
        val mine = createActiveUser()
        val other = createActiveUser()
        val token = mintAccessToken(mine)
        val (_, orderPublicIds) =
            seedOrderGroup(
                other,
                listOf(specOf(71L, "남의상점", "남의 상품", 5000L, OrderStatus.DELIVERED)),
                OrderGroupStatus.PAID,
                receiverName = "남의수령인",
                contactNumber = "01099998888",
            )
        seedShipment(orderPublicIds.first(), "CJ", "CJ대한통운", "999999999999")

        val body =
            mockMvc
                .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(50000))
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertTrue(!body.contains("남의수령인"), "타인 배송지 수령인이 응답에 새면 안 된다: $body")
        assertTrue(!body.contains("01099998888"), "타인 연락처가 응답에 새면 안 된다: $body")
        assertTrue(!body.contains("999999999999"), "타인 운송장 번호가 응답에 새면 안 된다: $body")
    }

    @Test
    fun `상세는 배송완료된 운송장의 완료 시각과 마지막 추적 상태를 싣는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val deliveredAt = LocalDateTime.now().minusDays(1).withNano(0)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.DELIVERED)),
                OrderGroupStatus.PAID,
            )
        seedShipment(orderPublicIds.first(), "CJ", "CJ대한통운", "123456789012")
        seedDeliveredTracking(orderPublicIds.first(), "배송완료", deliveredAt)

        val body = detailBody(token, orderPublicIds.first())

        assertEquals(
            deliveredAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
            OffsetDateTime.parse(JsonPath.read<String>(body, "$.data.shipment.deliveredAt")),
            "배송완료 시각을 그대로 실어야 한다",
        )
        assertEquals("배송완료", JsonPath.read<String>(body, "$.data.shipment.lastTrackingStatus"), "마지막 추적 상태를 실어야 한다")
    }

    @Test
    fun `철회된 클레임은 진행 중으로 싣지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.PAID)),
                OrderGroupStatus.PAID,
            )
        seedRequestedClaim(orderPublicIds.first())
        tx.executeWithoutResult { loadClaims().first().withdrawByBuyer() }

        assertNull(
            JsonPath.parse(detailBody(token, orderPublicIds.first())).read<Any?>("$.data.activeClaim"),
            "철회된 클레임은 품목을 풀어주므로 진행 중으로 실리면 안 된다",
        )
    }

    @Test
    fun `승인 후 환불 대기 중인 클레임은 싣고 환불이 끝나면 비운다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.PAID)),
                OrderGroupStatus.PAID,
            )
        seedRequestedClaim(orderPublicIds.first())
        tx.executeWithoutResult {
            loadClaims().first().apply {
                approveBySeller(null)
                markRefundRequested()
            }
        }

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.activeClaim.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.activeClaim.refundStatus").value("REQUESTED"))

        tx.executeWithoutResult { loadClaims().first().markRefunded() }

        assertNull(
            JsonPath.parse(detailBody(token, orderPublicIds.first())).read<Any?>("$.data.activeClaim"),
            "환불까지 끝난 클레임은 종결이라 진행 중으로 실리면 안 된다",
        )
    }

    @Test
    fun `환불에 실패한 클레임은 미해결이라 계속 싣는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 64000L, OrderStatus.PAID)),
                OrderGroupStatus.PAID,
            )
        seedRequestedClaim(orderPublicIds.first())
        tx.executeWithoutResult {
            loadClaims().first().apply {
                approveBySeller(null)
                markRefundRequested()
                markRefundFailed()
            }
        }

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.activeClaim.status").value("REFUND_FAILED"))
            .andExpect(jsonPath("$.data.activeClaim.refundStatus").value("REFUND_FAILED"))
    }

    @Test
    fun `미해결 클레임이 여러 건이면 가장 나중 요청 한 건을 싣는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val orderPublicId = seedOrderWithItemCount(buyerId, 2, OrderStatus.PAID)
        val first = seedRequestedClaim(orderPublicId, itemIndex = 0)
        val second = seedRequestedClaim(orderPublicId, itemIndex = 1)

        mockMvc
            .perform(get("/api/v1/orders/$orderPublicId").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.activeClaim.claimPublicId").value(second))

        assertTrue(first != second, "서로 다른 품목에 걸린 미해결 클레임 두 건이어야 최신 선택이 검증된다")
    }

    /**
     * 통계는 프로퍼티가 아니라 런타임에 켬. properties를 바꾸면 컨텍스트가 갈라져 공유 MySQL 컨테이너가 조기 종료될 수 있음.
     */
    @Nested
    inner class DetailQueryCount {
        private lateinit var statistics: Statistics

        @BeforeEach
        fun enableStatistics() {
            statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
            statistics.isStatisticsEnabled = true
        }

        @Test
        fun `품목이 늘어도 상세 조회 쿼리 수는 그대로다`() {
            val buyerId = createActiveUser()
            val token = mintAccessToken(buyerId)
            val oneItem = seedOrderWithItemCount(buyerId, 1)
            val fiveItems = seedOrderWithItemCount(buyerId, 5)

            val withOneItem = countStatementsOnDetail(token, oneItem)
            val withFiveItems = countStatementsOnDetail(token, fiveItems)

            assertEquals(1, JsonPath.read<List<*>>(detailBody(token, oneItem), "$.data.items").size, "품목 1개 주문이어야 한다")
            assertEquals(5, JsonPath.read<List<*>>(detailBody(token, fiveItems), "$.data.items").size, "품목 5개가 전부 실려야 한다")
            assertEquals(
                withOneItem,
                withFiveItems,
                "품목마다 운송장·클레임을 단건 조회하면 품목 수만큼 쿼리가 늘어난다 (1개=$withOneItem, 5개=$withFiveItems)",
            )
            assertTrue(withFiveItems in 1..9, "상세 조회 쿼리 수가 예상 범위를 벗어났다: $withFiveItems")
        }

        private fun countStatementsOnDetail(
            token: String,
            orderPublicId: String,
        ): Long {
            statistics.clear()
            detailBody(token, orderPublicId)
            return statistics.prepareStatementCount
        }
    }

    private companion object {
        private const val RECEIVER_NAME = "김구매"
        private const val CONTACT_NUMBER = "01012345678"
    }
}
