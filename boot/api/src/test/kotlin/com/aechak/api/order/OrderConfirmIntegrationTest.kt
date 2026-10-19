package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.user.point.facade.PointFacade
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.command.EarnPurchaseRewardCommand
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.user.user.User
import com.aechak.infra.persistence.order.order.OrderRepositoryAdapter
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 구매자 구매확정 통합. 확정은 비가역이고 적립금·리뷰·정산의 기산점이라
 * 전이 선점과 적립 멱등을 Testcontainers 실 MySQL로 고정한다(H2 금지).
 */
@Import(OrderConfirmIntegrationTest.TransitionRaceConfig::class)
class OrderConfirmIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var transitionBarrier: TransitionBarrier

    @Autowired
    private lateinit var transitionHook: TransitionHook

    @Autowired
    private lateinit var pointFailureSwitch: PointFailureSwitch

    private lateinit var mockMvc: MockMvc
    private var buyerId = 0L
    private lateinit var token: String
    private var sig = 0

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        buyerId = createActiveUser()
        token = mintAccessToken(buyerId)
        seedGlobalSettlementFeePolicy()
    }

    @AfterEach
    fun resetDevices() {
        transitionBarrier.disable()
        transitionHook.disarm()
        pointFailureSwitch.enabled = false
    }

    @Test
    fun `배송완료 주문을 확정하면 204와 함께 구매확정 시각과 확정 유형이 남는다`() {
        val fixture = seedOrder()

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        val order = findOrder(fixture.orderPublicId)
        assertEquals(OrderStatus.PURCHASE_CONFIRMED, order.status)
        assertNotNull(order.purchaseConfirmedAt, "확정 시각이 남아야 한다")
        assertEquals(ConfirmType.MANUAL, order.confirmType, "구매자가 직접 누른 확정은 MANUAL이어야 한다")
    }

    @Test
    fun `확정은 이력 1행을 남기고 발생 주체는 구매자다`() {
        val fixture = seedOrder()

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        assertEquals(
            listOf(Triple(OrderStatus.DELIVERED, OrderStatus.PURCHASE_CONFIRMED, TriggeredBy.BUYER)),
            histories(fixture.orderPublicId),
        )
    }

    @Test
    fun `확정하면 할인 안분액을 뺀 상품금액의 0_5퍼센트를 적립하고 잔액과 갱신 시각이 함께 움직인다`() {
        // 10,000 × 2 − 500 = 19,500 → 0.5%는 97.5원이고 1원 미만은 버린다
        val fixture = seedOrder(unitPrice = 10_000L, quantity = 2, discountAllocatedAmount = 500L)
        val balanceBefore = pointBalance(buyerId)
        val updatedAtBefore = userUpdatedAt(buyerId)

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        val rewards = rewards(fixture.orderPublicId)
        assertEquals(1, rewards.size, "적립 원장은 1행이어야 한다")
        assertEquals(97L, rewards.single().amount, "배송비를 뺀 상품금액 기준으로 내림 적립해야 한다")
        assertEquals("EARN", rewards.single().transactionType.name)
        assertEquals("ORDER", rewards.single().sourceType)
        assertEquals(fixture.orderId, rewards.single().sourceId, "출처는 확정된 셀러 주문이어야 한다")
        assertEquals(balanceBefore + 97L, pointBalance(buyerId), "잔액 캐시도 함께 올라야 한다")
        assertTrue(
            userUpdatedAt(buyerId).isAfter(updatedAtBefore),
            "잔액 가산은 원자 UPDATE라 갱신 시각을 쿼리에서 함께 SET해야 한다",
        )
    }

    @Test
    fun `적립액이 1원에 못 미치면 확정만 하고 원장은 남기지 않는다`() {
        val fixture = seedOrder(unitPrice = 100L, quantity = 1, discountAllocatedAmount = 0L)

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        assertEquals(OrderStatus.PURCHASE_CONFIRMED, findOrder(fixture.orderPublicId).status)
        assertEquals(emptyList<Reward>(), rewards(fixture.orderPublicId), "0원 적립 원장은 남기지 않는다")
        assertEquals(0L, pointBalance(buyerId))
    }

    @Test
    fun `두 번 확정하면 409와 50007이고 적립금은 한 번만 지급된다`() {
        val fixture = seedOrder()

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)
        mockMvc
            .perform(confirmRequest(token, fixture.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50007))

        assertEquals(1, rewards(fixture.orderPublicId).size, "재호출로 적립이 두 번 되면 안 된다")
        assertEquals(97L, pointBalance(buyerId))
    }

    @Test
    fun `배송완료가 아닌 주문은 409와 50006으로 거절한다`() {
        val shipping = seedOrder(status = OrderStatus.SHIPPING)

        mockMvc
            .perform(confirmRequest(token, shipping.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50006))

        assertEquals(OrderStatus.SHIPPING, findOrder(shipping.orderPublicId).status)
        assertEquals(emptyList<Reward>(), rewards(shipping.orderPublicId))
        assertEquals(emptyList<Triple<OrderStatus, OrderStatus, TriggeredBy>>(), histories(shipping.orderPublicId))
    }

    @Test
    fun `결제완료 주문도 409와 50006으로 거절한다`() {
        val paid = seedOrder(status = OrderStatus.PAID)

        mockMvc
            .perform(confirmRequest(token, paid.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50006))
    }

    @Test
    fun `진행 중 클레임이 있으면 409와 50006으로 거절하고 상태를 그대로 둔다`() {
        val fixture = seedOrder()
        seedClaim(fixture, ClaimStatus.CLAIM_REQUESTED)

        mockMvc
            .perform(confirmRequest(token, fixture.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50006))

        // 확정은 비가역이라 클레임 결론 전에 확정되면 되돌릴 경로가 없다 — 선점 UPDATE까지 롤백돼야 한다
        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderPublicId).status)
        assertNull(findOrder(fixture.orderPublicId).purchaseConfirmedAt)
        assertEquals(emptyList<Reward>(), rewards(fixture.orderPublicId))
        assertEquals(emptyList<Triple<OrderStatus, OrderStatus, TriggeredBy>>(), histories(fixture.orderPublicId))
    }

    @Test
    fun `수거 중 클레임도 확정을 막는다`() {
        val fixture = seedOrder()
        seedClaim(fixture, ClaimStatus.COLLECTING)

        mockMvc
            .perform(confirmRequest(token, fixture.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50006))
    }

    @Test
    fun `거부된 클레임은 확정을 막지 않는다`() {
        val fixture = seedOrder()
        seedClaim(fixture, ClaimStatus.REJECTED)

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        assertEquals(OrderStatus.PURCHASE_CONFIRMED, findOrder(fixture.orderPublicId).status)
    }

    @Test
    fun `타인 주문은 존재를 숨겨 404와 50000으로 거절한다`() {
        val strangerId = createActiveUser()
        val fixture = seedOrder(buyer = strangerId)

        mockMvc
            .perform(confirmRequest(token, fixture.orderPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))

        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderPublicId).status)
        assertEquals(emptyList<Reward>(), rewards(fixture.orderPublicId))
    }

    @Test
    fun `없는 주문은 404와 50000으로 거절한다`() {
        mockMvc
            .perform(confirmRequest(token, "01J0000000000000000000NONE"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `토큰이 없으면 401을 준다`() {
        val fixture = seedOrder()

        mockMvc
            .perform(post("$PATH_PREFIX/${fixture.orderPublicId}/confirm"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `확정한 품목은 미작성 리뷰 목록에 나온다`() {
        val fixture = seedOrder()

        assertEquals(emptyList<Long>(), unreviewedOrderItemIds(), "확정 전에는 리뷰 대기 목록이 비어 있어야 한다")

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        assertEquals(listOf(fixture.orderItemId), unreviewedOrderItemIds(), "확정하면 리뷰 작성 대기 목록이 열려야 한다")
    }

    @Test
    fun `환불 실패로 멈춘 클레임도 확정을 막는다`() {
        val fixture = seedOrder()
        seedClaim(fixture, ClaimStatus.REFUND_FAILED)

        mockMvc
            .perform(confirmRequest(token, fixture.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50006))

        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderPublicId).status)
    }

    @Test
    fun `주문을 읽은 뒤 클레임이 접수돼도 그 클레임을 보고 거절한다`() {
        val fixture = seedOrder()
        // 선점 UPDATE 직전에 다른 트랜잭션이 클레임을 커밋한다 — 확정 트랜잭션이 자기 스냅샷에 갇히면 이 클레임을 놓친다
        transitionHook.armOnce { commitClaimInOtherTransaction(fixture) }

        mockMvc
            .perform(confirmRequest(token, fixture.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50006))

        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderPublicId).status)
        assertNull(findOrder(fixture.orderPublicId).purchaseConfirmedAt)
        assertEquals(emptyList<Reward>(), rewards(fixture.orderPublicId))
        assertEquals(emptyList<Triple<OrderStatus, OrderStatus, TriggeredBy>>(), histories(fixture.orderPublicId))
    }

    @Test
    fun `이미 같은 멱등키의 적립 원장이 있으면 다시 지급하지 않는다`() {
        val fixture = seedOrder()
        seedReward(fixture, amount = 1L)

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        assertEquals(OrderStatus.PURCHASE_CONFIRMED, findOrder(fixture.orderPublicId).status)
        assertEquals(listOf(1L), rewards(fixture.orderPublicId).map { it.amount }, "멱등키 선검사가 이중 지급을 막아야 한다")
        assertEquals(0L, pointBalance(buyerId), "잔액도 다시 오르면 안 된다")
    }

    @Test
    fun `적립이 실패하면 확정도 함께 롤백된다`() {
        val fixture = seedOrder()
        pointFailureSwitch.enabled = true

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().is5xxServerError)

        val order = findOrder(fixture.orderPublicId)
        assertEquals(OrderStatus.DELIVERED, order.status, "확정됐는데 적립이 없는 주문이 남으면 안 된다")
        assertNull(order.purchaseConfirmedAt)
        assertNull(order.confirmType)
        assertEquals(emptyList<Triple<OrderStatus, OrderStatus, TriggeredBy>>(), histories(fixture.orderPublicId))
        assertEquals(emptyList<Reward>(), rewards(fixture.orderPublicId), "원장도 함께 되돌아가야 한다")
        assertEquals(0L, pointBalance(buyerId), "잔액 캐시도 함께 되돌아가야 한다")
    }

    @Test
    fun `동시에 확정하면 하나만 204이고 적립 원장도 1행이다`() {
        val fixture = seedOrder()
        transitionBarrier.enable()

        val results = raceTwo { confirmRequest(token, fixture.orderPublicId) }

        assertEquals(listOf(204, 409), results.map { it.first }.sorted(), "하나만 확정돼야 한다")
        assertEquals(
            50007,
            JsonPath.read<Int>(results.first { it.first == 409 }.second, "$.errorCode"),
            "배송완료에서 나가는 전이는 구매확정뿐이라 경합의 패자는 이미 확정된 것이다",
        )
        assertEquals(1, rewards(fixture.orderPublicId).size, "적립이 두 번 되면 안 된다")
        assertEquals(97L, pointBalance(buyerId))
        assertEquals(1, histories(fixture.orderPublicId).size, "이력도 1행이어야 한다")
    }

    private fun confirmRequest(
        accessToken: String,
        orderPublicId: String,
    ): MockHttpServletRequestBuilder =
        post("$PATH_PREFIX/$orderPublicId/confirm")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")

    private fun unreviewedOrderItemIds(): List<Long> {
        val body =
            mockMvc
                .perform(
                    get("/api/v1/users/me/reviews")
                        .param("tab", "unreviewed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                ).andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read<List<Int>>(body, "$.data.items[*].orderItemId").map { it.toLong() }
    }

    private fun findOrder(orderPublicId: String): Order =
        tx.execute {
            em
                .createQuery("select o from Order o where o.publicId = :publicId", Order::class.java)
                .setParameter("publicId", orderPublicId)
                .singleResult
        }!!

    private fun histories(orderPublicId: String): List<Triple<OrderStatus, OrderStatus, TriggeredBy>> =
        tx.execute {
            em
                .createQuery(
                    "select h from OrderStatusHistory h where h.order.publicId = :publicId order by h.id",
                    com.aechak.domain.order.order.OrderStatusHistory::class.java,
                ).setParameter("publicId", orderPublicId)
                .resultList
                .map { Triple(it.fromStatus!!, it.toStatus, it.triggeredBy) }
        }!!

    private fun rewards(orderPublicId: String): List<Reward> =
        tx.execute {
            em
                .createQuery(
                    "select t from PointTransaction t where t.idempotencyKey = :key",
                    com.aechak.domain.user.point.PointTransaction::class.java,
                ).setParameter("key", "EARN:PURCHASE:$orderPublicId")
                .resultList
                .map { Reward(it.amount, it.transactionType, it.sourceType, it.sourceId) }
        }!!

    private fun pointBalance(userId: Long): Long = tx.execute { em.find(User::class.java, userId).pointBalance }!!

    private fun userUpdatedAt(userId: Long): LocalDateTime = tx.execute { em.find(User::class.java, userId).updatedAt }!!

    /** 확정 요청과 겹친 클레임 접수를 재현한다 — 별 스레드라 자기 트랜잭션에서 커밋된다. */
    private fun commitClaimInOtherTransaction(fixture: Fixture) {
        val pool = Executors.newSingleThreadExecutor()
        val task = pool.submit { tx.execute { seedClaimRow(fixture) } }
        try {
            task.get(20, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            task.cancel(true)
            throw e
        } finally {
            // 남은 작업이 다음 테스트의 데이터를 건드리지 않게 종료까지 기다린다
            pool.shutdownNow()
            pool.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    private fun seedReward(
        fixture: Fixture,
        amount: Long,
    ) = tx.execute {
        em.persist(
            com.aechak.domain.user.point.PointTransaction.record(
                buyer = em.find(User::class.java, buyerId),
                amount = amount,
                transactionType = com.aechak.domain.user.point.enums.PointTransactionType.EARN,
                idempotencyKey = "EARN:PURCHASE:${fixture.orderPublicId}",
                sourceType = "ORDER",
                sourceId = fixture.orderId,
            ),
        )
        em.flush()
    }

    private fun seedClaim(
        fixture: Fixture,
        status: ClaimStatus,
    ) = tx.execute { seedClaimRow(fixture, status) }

    /**
     * 반품은 요청 경로가 아직 열리지 않았고 취소도 발송 전 상태에서만 받는다 —
     * 결제완료로 잠시 올려 취소 클레임을 만든 뒤 유형·상태를 쿼리로 옮긴다. 주문 상태는 원래 값으로 되돌린다.
     */
    private fun seedClaimRow(
        fixture: Fixture,
        status: ClaimStatus = ClaimStatus.CLAIM_REQUESTED,
    ) {
        val order = em.find(Order::class.java, fixture.orderId)
        val originalStatus = order.status
        forceOrderStatus(order, OrderStatus.PAID)
        val claim =
            Claim.request(
                order = order,
                claimType = ClaimType.CANCEL,
                reasonCode = "DEFECTIVE",
                reasonDetail = null,
                items = listOf(ClaimItem.of(order.items.first(), quantity = 1, refundAmount = 10_000L)),
                autoApproveDueAt = LocalDateTime.now().plusDays(2),
            )
        forceOrderStatus(order, originalStatus)
        em.persist(claim)
        em.flush()
        em
            .createQuery("update Claim c set c.claimType = :claimType where c.id = :id")
            .setParameter("claimType", ClaimType.RETURN)
            .setParameter("id", claim.id)
            .executeUpdate()
        // 요청 상태 외의 상태는 전이 API가 아직 없어 쿼리로 심는다 — 전이 규칙 자체는 이 테스트의 대상이 아니다
        em
            .createQuery("update Claim c set c.status = :status where c.id = :id")
            .setParameter("status", status)
            .setParameter("id", claim.id)
            .executeUpdate()
    }

    private fun forceOrderStatus(
        order: Order,
        status: OrderStatus,
    ) {
        Order::class.java
            .getDeclaredField("status")
            .apply { isAccessible = true }
            .set(order, status)
    }

    private fun seedOrder(
        buyer: Long = buyerId,
        status: OrderStatus = OrderStatus.DELIVERED,
        unitPrice: Long = 10_000L,
        quantity: Int = 2,
        discountAllocatedAmount: Long = 500L,
    ): Fixture =
        tx.execute {
            val tag = "s${sig++}"
            val sellerUserId = ensureSeller()
            val category = Category.create(null, 1, "카테고리-$tag", null, 1)
            em.persist(category)
            val product =
                Product.register(category, sellerUserId, "사료-$tag", null, "products/$tag.jpg", unitPrice, null, null, null)
            em.persist(product)
            em.flush()
            val version =
                ProductVersion.snapshot(
                    product = product,
                    versionNo = 1,
                    nameSnapshot = "사료-$tag",
                    priceSnapshot = unitPrice,
                    statusSnapshot = SaleStatus.ON_SALE,
                    thumbnailKeySnapshot = "products/$tag.jpg",
                    changedBy = VersionChangedBy.SELLER,
                )
            em.persist(version)
            val combination = OptionCombination.create(product, "블랙 / L", 0L, 100, "sig-$tag")
            em.persist(combination)
            em.flush()

            val group =
                OrderGroup.create(
                    buyerId = buyer,
                    deliveryAddressId = 0L,
                    deliveryAddress = deliveryAddressSnapshot(),
                    usedPoint = 0L,
                    totalProductAmount = unitPrice * quantity,
                    totalShippingFee = 3_000L,
                    idempotencyKey = "idem-$tag-$buyer",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )
            em.persist(group)
            val item =
                OrderItem.of(
                    productId = product.id,
                    optionCombinationId = combination.id,
                    optionNameSnapshot = combination.name,
                    quantity = quantity,
                    unitPriceSnapshot = unitPrice,
                    discountAllocatedAmount = discountAllocatedAmount,
                    productVersionId = version.id,
                )
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = sellerUserId,
                    sellerNameSnapshot = "store-$sellerUserId",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = 3_000L,
                    baseShippingFeeSnapshot = null,
                    freeShippingThresholdSnapshot = null,
                    items = listOf(item),
                )
            em.persist(order)
            em.flush()
            em
                .createQuery("update Order o set o.status = :status where o.id = :id")
                .setParameter("status", status)
                .setParameter("id", order.id)
                .executeUpdate()
            Fixture(orderPublicId = order.publicId, orderId = order.id, orderItemId = item.id)
        }!!

    /** 셀러 1명으로 충분하다 — 주문의 sellerId가 가리키는 행만 있으면 된다. */
    private fun ensureSeller(): Long {
        if (em.find(Seller::class.java, SELLER_USER_ID) == null) {
            em.persist(Seller.open(userId = SELLER_USER_ID, storeName = "store-$SELLER_USER_ID", baseShippingFee = 3_000L))
            em.flush()
        }
        return SELLER_USER_ID
    }

    private fun deliveryAddressSnapshot() =
        DeliveryAddressSnapshot(
            receiverNameEnc = "enc-receiver",
            contactNumberEnc = "enc-contact",
            zipCode = "12345",
            baseAddress = "서울시 애착구 멍냥로 1",
            detailAddress = null,
            deliveryMemo = null,
        )

    private fun raceTwo(request: () -> MockHttpServletRequestBuilder): List<Pair<Int, String>> {
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val futures =
                listOf(request(), request()).map { builder ->
                    pool.submit<Pair<Int, String>> {
                        ready.countDown()
                        start.await()
                        val response = mockMvc.perform(builder).andReturn().response
                        response.status to response.getContentAsString(Charsets.UTF_8)
                    }
                }
            ready.await()
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }
    }

    private data class Fixture(
        val orderPublicId: String,
        val orderId: Long,
        val orderItemId: Long,
    )

    private data class Reward(
        val amount: Long,
        val transactionType: com.aechak.domain.user.point.enums.PointTransactionType,
        val sourceType: String?,
        val sourceId: Long?,
    )

    /** 두 요청이 주문 조회를 끝낸 지점에서 만나게 하는 장치. enable한 테스트에서만 동작한다. */
    class TransitionBarrier {
        private val barrier = CyclicBarrier(2)
        private val arrivals = AtomicInteger()

        @Volatile
        private var enabled = false

        /** 싱글턴이라 테스트 간에 도착 수가 남는다 — 켤 때마다 되감는다. */
        fun enable() {
            arrivals.set(0)
            barrier.reset()
            enabled = true
        }

        fun disable() {
            enabled = false
        }

        fun awaitBothArrived() {
            if (enabled && arrivals.getAndIncrement() < BOTH) {
                barrier.await(AWAIT_SECONDS, TimeUnit.SECONDS)
            }
        }

        private companion object {
            private const val BOTH = 2
            private const val AWAIT_SECONDS = 10L
        }
    }

    /** 선점 UPDATE 직전에 끼워 넣을 동작. 켠 테스트에서만 돌고, 동시 요청에서도 한 번만 실행된다. */
    class TransitionHook {
        private val action = AtomicReference<(() -> Unit)?>(null)

        fun armOnce(action: () -> Unit) {
            this.action.set(action)
        }

        fun disarm() {
            action.set(null)
        }

        fun fire() {
            action.getAndSet(null)?.invoke()
        }
    }

    /** 적립 실패를 주입하는 장치. 켠 테스트에서만 던진다. */
    class PointFailureSwitch {
        @Volatile
        var enabled = false
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TransitionRaceConfig {
        @Bean
        fun transitionBarrier(): TransitionBarrier = TransitionBarrier()

        @Bean
        fun transitionHook(): TransitionHook = TransitionHook()

        @Bean
        fun pointFailureSwitch(): PointFailureSwitch = PointFailureSwitch()

        @Bean
        @Primary
        fun barrieredOrderRepository(
            delegate: OrderRepositoryAdapter,
            barrier: TransitionBarrier,
            hook: TransitionHook,
        ): OrderRepository =
            object : OrderRepository by delegate {
                override fun transitionStatusIf(
                    id: Long,
                    expected: OrderStatus,
                    next: OrderStatus,
                ): Boolean {
                    hook.fire()
                    barrier.awaitBothArrived()
                    return delegate.transitionStatusIf(id, expected, next)
                }
            }

        @Bean
        @Primary
        fun failablePointUseCase(
            delegate: PointFacade,
            switch: PointFailureSwitch,
        ): PointUseCase =
            object : PointUseCase by delegate {
                override fun earnPurchaseReward(command: EarnPurchaseRewardCommand) {
                    delegate.earnPurchaseReward(command)
                    // 원장과 잔액이 이미 쓰인 뒤에 터뜨린다 — 양쪽 쓰기의 롤백까지 보려면 위임 후여야 한다
                    if (switch.enabled) {
                        throw IllegalStateException("적립 실패 주입")
                    }
                }
            }
    }

    private companion object {
        private const val PATH_PREFIX = "/api/v1/orders"
        private const val SELLER_USER_ID = 77L
    }
}
