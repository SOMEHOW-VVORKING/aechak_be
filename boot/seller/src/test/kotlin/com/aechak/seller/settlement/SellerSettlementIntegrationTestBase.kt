package com.aechak.seller.settlement

import com.aechak.api.support.IntegrationTestBase
import com.aechak.api.support.SettlementFeePolicyFixture
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.feepolicy.SettlementFeePolicy
import com.aechak.domain.settlement.payout.Payout
import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.policy.SettlementCalendar
import com.aechak.domain.settlement.settlement.policy.ShippingFeeAllocationPolicy
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 셀러 정산 조회 통합 테스트 공용 지원. 구매확정 EP는 다른 보트에 있어 원장을 직접 심는데,
 * 금액·배송비 안분·정산월 파생은 구매확정 경로가 쓰는 도메인 팩토리와 정책을 그대로 태운다 —
 * 심는 값이 실제 원장과 다른 모양이면 조회 테스트가 거짓으로 통과한다.
 */
abstract class SellerSettlementIntegrationTestBase : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    protected lateinit var mockMvc: MockMvc
    protected var sellerUserId = 0L
    protected lateinit var token: String
    private var seq = 0

    @BeforeEach
    fun setUpSellerAndMockMvc() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        openSeller(sellerUserId)
    }

    protected fun openSeller(userId: Long) {
        tx.execute {
            em.persist(Seller.open(userId, "애착상회$userId", BASE_SHIPPING_FEE))
            em.flush()
        }
    }

    protected fun openOtherSeller(): Pair<Long, String> {
        val otherUserId = createActiveUser()
        openSeller(otherUserId)
        return otherUserId to mintAccessToken(otherUserId)
    }

    protected fun changeSellerStatus(
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

    /**
     * 구매확정된 주문 하나와 그 품목별 정산 원장을 심는다.
     * 원장은 전부 PENDING(지급 미편성)으로 시작하고, 상태는 [holdSettlement] 등으로 따로 옮긴다.
     */
    protected fun seedSettledOrder(
        sellerId: Long = sellerUserId,
        confirmedAt: LocalDateTime = DEFAULT_CONFIRMED_AT,
        lines: List<Line> = listOf(Line(unitPrice = 10_000L, quantity = 2, discountAllocatedAmount = 500L)),
        shippingFee: Long = BASE_SHIPPING_FEE,
        sellerCouponBurden: Long = 0L,
        feePolicy: SettlementFeePolicy = SettlementFeePolicyFixture.global(),
    ): SettledOrder =
        tx.execute {
            val tag = "s${seq++}"
            val category = Category.create(null, Category.ROOT_DEPTH, "카테고리-$tag", null, 1)
            em.persist(category)
            val names = lines.indices.map { "연어 건사료-$tag-$it" }
            val items =
                lines.mapIndexed { index, line ->
                    val product =
                        Product.register(
                            category,
                            sellerId,
                            names[index],
                            null,
                            "products/$tag-$index.jpg",
                            line.unitPrice,
                            null,
                            null,
                            null,
                        )
                    em.persist(product)
                    em.flush()
                    val version =
                        ProductVersion.snapshot(
                            product = product,
                            versionNo = 1,
                            nameSnapshot = names[index],
                            priceSnapshot = line.unitPrice,
                            statusSnapshot = SaleStatus.ON_SALE,
                            thumbnailKeySnapshot = "products/$tag-$index.jpg",
                            changedBy = VersionChangedBy.SELLER,
                        )
                    em.persist(version)
                    val combination = OptionCombination.create(product, "기본 / 1개", 0L, 100, "sig-$tag-$index")
                    em.persist(combination)
                    em.flush()
                    OrderItem.of(
                        productId = product.id,
                        optionCombinationId = combination.id,
                        optionNameSnapshot = combination.name,
                        quantity = line.quantity,
                        unitPriceSnapshot = line.unitPrice,
                        discountAllocatedAmount = line.discountAllocatedAmount,
                        productVersionId = version.id,
                    )
                }
            val group =
                OrderGroup.create(
                    buyerId = BUYER_ID,
                    deliveryAddressId = 0L,
                    deliveryAddress = deliveryAddressSnapshot(),
                    usedPoint = 0L,
                    totalProductAmount = items.sumOf { it.productAmountAfterDiscount() },
                    totalShippingFee = shippingFee,
                    idempotencyKey = "settlement-query-$tag",
                    expiresAt = confirmedAt.plusMinutes(10),
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
                    items = items,
                )
            em.persist(order)
            em.flush()
            confirmOrder(order.id, confirmedAt)

            val orderItemIds = order.items.map { it.id }
            val allocated = ShippingFeeAllocationPolicy.allocate(shippingFee, orderItemIds)
            val confirmedOn = SettlementCalendar.settlementDateOf(confirmedAt)
            val settlements =
                order.items.map { item ->
                    val settlement =
                        Settlement.createOnPurchaseConfirm(
                            feePolicy = feePolicy,
                            sellerId = sellerId,
                            orderId = order.id,
                            orderItemId = item.id,
                            grossAmount = item.productAmountAfterDiscount(),
                            shippingFee = allocated.getValue(item.id),
                            sellerCouponBurden = sellerCouponBurden,
                            confirmedOn = confirmedOn,
                        )
                    em.persist(settlement)
                    settlement
                }
            em.flush()
            SettledOrder(
                orderPublicId = order.publicId,
                orderId = order.id,
                orderItemIds = orderItemIds,
                productNames = names,
                settlementIds = settlements.map { it.id },
            )
        }!!

    private fun confirmOrder(
        orderId: Long,
        confirmedAt: LocalDateTime,
    ) {
        em
            .createQuery(
                "update Order o set o.status = :status, o.purchaseConfirmedAt = :at, o.confirmType = :type where o.id = :id",
            ).setParameter("status", OrderStatus.PURCHASE_CONFIRMED)
            .setParameter("at", confirmedAt)
            .setParameter("type", ConfirmType.MANUAL)
            .setParameter("id", orderId)
            .executeUpdate()
    }

    protected fun holdSettlement(settlementId: Long) {
        tx.execute {
            em.find(Settlement::class.java, settlementId).hold()
            em.flush()
        }
    }

    protected fun cancelSettlement(settlementId: Long) {
        tx.execute {
            em.find(Settlement::class.java, settlementId).cancel()
            em.flush()
        }
    }

    protected fun paySettlement(settlementId: Long) {
        tx.execute {
            val settlement = em.find(Settlement::class.java, settlementId)
            settlement.markPaid(persistPayout(settlement.sellerId, settlement.netAmount))
            em.flush()
        }
    }

    protected fun deductRefund(
        settlementId: Long,
        amount: Long,
    ) {
        tx.execute {
            em.find(Settlement::class.java, settlementId).applyRefundDeduction(amount)
            em.flush()
        }
    }

    /**
     * 지급 묶음에만 편성된 상태(PENDING + payout)를 만든다.
     * 집행 배치가 아직 없어 도메인 메서드로는 이 상태에 도달할 수 없어 컬럼을 직접 쓴다.
     */
    protected fun scheduleSettlement(settlementId: Long) {
        tx.execute {
            val settlement = em.find(Settlement::class.java, settlementId)
            val payout = persistPayout(settlement.sellerId, settlement.netAmount)
            em.flush()
            em
                .createQuery("update Settlement s set s.payout = :payout where s.id = :id")
                .setParameter("payout", payout)
                .setParameter("id", settlementId)
                .executeUpdate()
        }
    }

    /** 정산 행에 저장된 값을 그대로 읽는다 — 기대값을 테스트에서 다시 계산하지 않기 위한 대조용 */
    protected fun settlementsOf(orderId: Long): List<Settlement> =
        tx.execute {
            em
                .createQuery("select s from Settlement s where s.orderId = :orderId order by s.orderItemId", Settlement::class.java)
                .setParameter("orderId", orderId)
                .resultList
        }!!

    private fun persistPayout(
        sellerId: Long,
        amount: Long,
    ): Payout {
        // 집행 배치는 기준일마다 한 행만 허용된다 — 테스트가 여러 묶음을 만들 수 있게 기준일을 따로 준다
        val batch = SettlementExecutionBatch.generate(LocalDate.of(2026, 7, 1).plusDays(seq++.toLong()), 1, amount)
        em.persist(batch)
        em.flush()
        val payout = Payout.create(batch, sellerId, amount, "PAYOUT:${batch.id}:$sellerId")
        em.persist(payout)
        em.flush()
        return payout
    }

    protected fun requestList(
        vararg params: Pair<String, String>,
        asToken: String = token,
    ): ResultActions =
        mockMvc.perform(
            params
                .fold(get(LIST_URL)) { request, (key, value) -> request.param(key, value) }
                .header(HttpHeaders.AUTHORIZATION, "Bearer $asToken"),
        )

    protected fun requestDetail(
        orderPublicId: String,
        asToken: String = token,
    ): ResultActions =
        mockMvc.perform(
            get("$LIST_URL/{orderPublicId}", orderPublicId).header(HttpHeaders.AUTHORIZATION, "Bearer $asToken"),
        )

    protected fun requestMonths(asToken: String = token): ResultActions =
        mockMvc.perform(get("$LIST_URL/months").header(HttpHeaders.AUTHORIZATION, "Bearer $asToken"))

    private fun deliveryAddressSnapshot() =
        DeliveryAddressSnapshot(
            receiverNameEnc = "enc-receiver",
            contactNumberEnc = "enc-contact",
            zipCode = "12345",
            baseAddress = "서울시 애착구 멍냥로 1",
            detailAddress = null,
            deliveryMemo = null,
        )

    protected data class Line(
        val unitPrice: Long,
        val quantity: Int,
        val discountAllocatedAmount: Long = 0L,
    )

    protected data class SettledOrder(
        val orderPublicId: String,
        val orderId: Long,
        val orderItemIds: List<Long>,
        val productNames: List<String>,
        val settlementIds: List<Long>,
    )

    protected companion object {
        const val LIST_URL = "/api/v1/sellers/me/settlements"
        const val BASE_SHIPPING_FEE = 3_000L
        const val BUYER_ID = 9_999L
        val DEFAULT_CONFIRMED_AT: LocalDateTime = LocalDateTime.of(2026, 6, 18, 10, 0)
        val PLATFORM_FEE_RATE: BigDecimal = SettlementFeePolicyFixture.PLATFORM_FEE_RATE
        val PG_FEE_RATE: BigDecimal = SettlementFeePolicyFixture.PG_FEE_RATE

        /** 기본 품목 하나 — 상품금액 19,500원에 배송비 3,000원이 전액 실린다 */
        const val ONE_LINE_PRODUCT_AMOUNT = 19_500
        const val ONE_LINE_COMMISSION = 1_560
        const val ONE_LINE_PG_FEE = 546
        const val ONE_LINE_FEE_AMOUNT = ONE_LINE_COMMISSION + ONE_LINE_PG_FEE
        const val ONE_LINE_FINAL_AMOUNT = 20_394

        /** 19,500원 품목 + 5,000원 품목 — 배송비 3,000원이 1,500/1,500으로 안분된다 */
        val TWO_LINES =
            listOf(
                Line(unitPrice = 10_000L, quantity = 2, discountAllocatedAmount = 500L),
                Line(unitPrice = 5_000L, quantity = 1),
            )
        const val TWO_LINES_PRODUCT_AMOUNT = 24_500
        const val TWO_LINES_FEE_AMOUNT = 2_646
        const val TWO_LINES_FINAL_AMOUNT = 24_854
    }
}
