package com.aechak.api.review

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.ReviewImage
import com.aechak.domain.review.review.enums.ReviewStatus
import com.aechak.domain.seller.seller.Seller
import com.jayway.jsonpath.JsonPath
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class MyReviewIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private val sellerId = 77L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `구매확정했고 리뷰를 안 쓴 품목을 상품 스냅샷과 함께 내린다`() {
        val buyerId = createActiveUser()
        val fixture = persistOrder(buyerId, productName = "강아지 사료", confirmedAt = LocalDateTime.now().minusDays(1))

        val body = getMyReviews(buyerId, tab = "unreviewed")

        assertEquals(
            listOf(fixture.productPublicId),
            JsonPath.read<List<String>>(body, "$.data.items[*].productPublicId"),
            "상품 상세는 publicId로 열리므로 내부 id가 아니라 publicId를 내려야 한다",
        )
        assertEquals(listOf("강아지 사료 v1"), JsonPath.read<List<String>>(body, "$.data.items[*].productName"), "상품명은 주문 시점 스냅샷이어야 한다")
        assertEquals(listOf("블랙 / L"), JsonPath.read<List<String>>(body, "$.data.items[*].optionName"), "주문 시점 옵션명이 나와야 한다")
        assertEquals(
            listOf("https://fake-cdn.local/products/강아지 사료-v1.jpg"),
            JsonPath.read<List<String>>(body, "$.data.items[*].productThumbnailUrl"),
            "썸네일은 현재 상품값이 아니라 주문 시점 스냅샷이어야 한다",
        )
        assertEquals(listOf(true), JsonPath.read<List<Boolean>>(body, "$.data.items[*].canReview"), "확정 1일 뒤면 작성할 수 있어야 한다")
    }

    @Test
    fun `구매확정일 내림차순으로 정렬하고 같은 주문 안에서는 품목 id 내림차순이다`() {
        val buyerId = createActiveUser()
        val older = persistOrder(buyerId, productName = "오래된 주문", confirmedAt = LocalDateTime.now().minusDays(5))
        val newer = persistOrder(buyerId, productName = "최근 주문", confirmedAt = LocalDateTime.now().minusDays(1), itemCount = 2)

        val body = getMyReviews(buyerId, tab = "unreviewed")

        val expected = newer.orderItemIds.sortedDescending() + older.orderItemIds
        assertEquals(
            expected,
            JsonPath.read<List<Int>>(body, "$.data.items[*].orderItemId").map { it.toLong() },
            "확정일 desc, 품목 id desc 순이어야 한다",
        )
    }

    @Test
    fun `30일이 지난 품목도 목록에 남기되 canReview는 false다`() {
        val buyerId = createActiveUser()
        val confirmedAt = LocalDateTime.now().minusDays(31)
        persistOrder(buyerId, productName = "만료된 주문", confirmedAt = confirmedAt)

        val body = getMyReviews(buyerId, tab = "unreviewed")

        assertEquals(listOf(false), JsonPath.read<List<Boolean>>(body, "$.data.items[*].canReview"), "31일 지난 건은 작성할 수 없어야 한다")
        assertEquals(
            confirmedAt.plusDays(Review.WRITE_WINDOW_DAYS).toLocalDate().toString(),
            JsonPath.read<String>(body, "$.data.items[0].reviewableUntil").substringBefore('T'),
            "작성 마감은 구매확정일 + 30일이어야 한다",
        )
    }

    @Test
    fun `이미 리뷰를 쓴 품목은 제외한다`() {
        val buyerId = createActiveUser()
        val fixture = persistOrder(buyerId, productName = "리뷰 완료", confirmedAt = LocalDateTime.now())
        tx.execute { persistReview(fixture, buyerId) }

        val body = getMyReviews(buyerId, tab = "unreviewed")

        assertEquals(emptyList<Int>(), JsonPath.read<List<Int>>(body, "$.data.items[*].orderItemId"), "작성 대기 목록에서 빠져야 한다")
    }

    @Test
    fun `리뷰를 썼다가 지운 품목도 재작성 불가라 목록에서 빠진다`() {
        val buyerId = createActiveUser()
        val fixture = persistOrder(buyerId, productName = "삭제한 리뷰", confirmedAt = LocalDateTime.now())
        tx.execute {
            val review = persistReview(fixture, buyerId)
            em.flush()
            setReviewStatus(review.id, ReviewStatus.DELETED)
        }

        val body = getMyReviews(buyerId, tab = "unreviewed")

        assertEquals(emptyList<Int>(), JsonPath.read<List<Int>>(body, "$.data.items[*].orderItemId"), "작성 대기 목록에서 빠져야 한다")
    }

    @Test
    fun `취소한 품목과 구매확정 전 주문은 제외한다`() {
        val buyerId = createActiveUser()
        val cancelled = persistOrder(buyerId, productName = "취소 품목", confirmedAt = LocalDateTime.now())
        tx.execute {
            em
                .createQuery("update OrderItem oi set oi.itemStatus = :st where oi.id = :id")
                .setParameter("st", OrderItemStatus.CANCELLED)
                .setParameter("id", cancelled.orderItemIds.first())
                .executeUpdate()
        }
        persistOrder(buyerId, productName = "미확정 주문", confirmedAt = null)

        val body = getMyReviews(buyerId, tab = "unreviewed")

        assertEquals(emptyList<Int>(), JsonPath.read<List<Int>>(body, "$.data.items[*].orderItemId"), "작성 대기 목록에서 빠져야 한다")
    }

    @Test
    fun `남의 주문은 목록에 나오지 않는다`() {
        val owner = createActiveUser()
        val stranger = createActiveUser()
        persistOrder(owner, productName = "남의 주문", confirmedAt = LocalDateTime.now())

        val body = getMyReviews(stranger, tab = "unreviewed")

        assertEquals(emptyList<Int>(), JsonPath.read<List<Int>>(body, "$.data.items[*].orderItemId"), "작성 대기 목록에서 빠져야 한다")
    }

    @Test
    fun `unreviewed 커서로 순회하면 모든 품목을 한 번씩 지나간다`() {
        val buyerId = createActiveUser()
        val fixture = persistOrder(buyerId, productName = "다품목 주문", confirmedAt = LocalDateTime.now(), itemCount = 3)

        val page1 = getMyReviews(buyerId, tab = "unreviewed", size = 2)
        assertTrue(JsonPath.read<Boolean>(page1, "$.data.hasNext"), "첫 페이지에는 다음이 있어야 한다")
        val page2 = getMyReviews(buyerId, tab = "unreviewed", size = 2, cursor = JsonPath.read(page1, "$.data.nextCursor"))
        assertFalse(JsonPath.read<Boolean>(page2, "$.data.hasNext"), "마지막 페이지에는 다음이 없어야 한다")

        val seen = readOrderItemIds(page1) + readOrderItemIds(page2)
        assertEquals(fixture.orderItemIds.sortedDescending(), seen, "커서 순회가 모든 품목을 한 번씩 지나가야 한다")
    }

    @Test
    fun `서로 다른 확정 시각에 걸쳐 커서로 순회해도 중복이나 누락이 없다`() {
        val buyerId = createActiveUser()
        // 마이크로초 정밀도 손실 시 동일 행 재조회
        val base = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.MICROS)
        val first = persistOrder(buyerId, productName = "주문A", confirmedAt = base)
        val second = persistOrder(buyerId, productName = "주문B", confirmedAt = base.plusNanos(1_000))

        val page1 = getMyReviews(buyerId, tab = "unreviewed", size = 1)
        val page2 = getMyReviews(buyerId, tab = "unreviewed", size = 1, cursor = JsonPath.read(page1, "$.data.nextCursor"))

        val seen = readOrderItemIds(page1) + readOrderItemIds(page2)
        assertEquals(
            listOf(second.orderItemIds.single(), first.orderItemIds.single()),
            seen,
            "확정 시각이 1마이크로초만 달라도 커서가 정확히 다음 행으로 넘어가야 한다",
        )
        assertFalse(JsonPath.read<Boolean>(page2, "$.data.hasNext"), "두 건을 다 지났으면 다음이 없어야 한다")
    }

    @Test
    fun `남은 건수가 size와 같으면 다음 페이지가 없다고 알린다`() {
        val buyerId = createActiveUser()
        persistOrder(buyerId, productName = "정확히 두 건", confirmedAt = LocalDateTime.now(), itemCount = 2)

        val body = getMyReviews(buyerId, tab = "unreviewed", size = 2)

        assertEquals(2, readOrderItemIds(body).size, "요청한 만큼 나와야 한다")
        assertFalse(JsonPath.read<Boolean>(body, "$.data.hasNext"), "size와 건수가 같으면 다음 페이지가 없어야 한다")
        assertNull(JsonPath.read<Any?>(body, "$.data.nextCursor"), "다음이 없으면 커서도 없어야 한다")
    }

    @Test
    fun `내가 쓴 리뷰를 사진과 상품 스냅샷까지 내린다`() {
        val buyerId = createActiveUser()
        val fixture = persistOrder(buyerId, productName = "사료", confirmedAt = LocalDateTime.now())
        tx.execute {
            persistReview(fixture, buyerId, content = "정말 만족합니다", images = listOf("reviews/a.jpg", "reviews/b.jpg"))
        }

        val body = getMyReviews(buyerId, tab = "written")

        assertEquals(listOf("정말 만족합니다"), JsonPath.read<List<String>>(body, "$.data.items[*].content"), "내가 쓴 리뷰 원문이 나와야 한다")
        assertEquals(listOf("사료 v1"), JsonPath.read<List<String>>(body, "$.data.items[*].productName"), "상품명은 주문 시점 스냅샷이어야 한다")
        assertEquals(
            listOf(fixture.productPublicId),
            JsonPath.read<List<String>>(body, "$.data.items[*].productPublicId"),
            "written도 publicId를 내려야 한다",
        )
        assertEquals("PUBLIC", JsonPath.read<String>(body, "$.data.items[0].reviewStatus"), "리뷰 상태를 함께 내려야 한다")
        assertEquals(
            listOf("https://fake-cdn.local/reviews/a.jpg", "https://fake-cdn.local/reviews/b.jpg"),
            JsonPath.read<List<String>>(body, "$.data.items[0].images[*].imageUrl"),
            "리뷰 사진이 정렬 순서대로 나와야 한다",
        )
    }

    @Test
    fun `마스킹된 내 리뷰는 마스킹된 본문을, 차단된 리뷰는 본문을 그대로 내린다`() {
        val buyerId = createActiveUser()
        val maskedFixture = persistOrder(buyerId, productName = "마스킹", confirmedAt = LocalDateTime.now())
        val blockedFixture = persistOrder(buyerId, productName = "차단", confirmedAt = LocalDateTime.now())
        tx.execute {
            val masked = persistReview(maskedFixture, buyerId, content = "마스킹된 원문")
            val blocked = persistReview(blockedFixture, buyerId, content = "차단된 원문")
            em.flush()
            maskReview(masked.id, "대체 문구입니다")
            setReviewStatus(blocked.id, ReviewStatus.BLOCKED)
        }

        val body = getMyReviews(buyerId, tab = "written")

        assertEquals(
            listOf("차단된 원문", "대체 문구입니다"),
            JsonPath.read<List<String>>(body, "$.data.items[*].content"),
            "마스킹된 리뷰는 남들이 보는 본문과 같아야 한다",
        )
        assertEquals(
            listOf("BLOCKED", "MASKED"),
            JsonPath.read<List<String>>(body, "$.data.items[*].reviewStatus"),
            "왜 안 보이는지 알 수 있게 상태를 내려야 한다",
        )
    }

    @Test
    fun `대체 문구 없이 마스킹된 리뷰는 블라인드 문구를 내린다`() {
        val buyerId = createActiveUser()
        val fixture = persistOrder(buyerId, productName = "대체문구없음", confirmedAt = LocalDateTime.now())
        tx.execute {
            val masked = persistReview(fixture, buyerId, content = "민감한 원문")
            em.flush()
            setReviewStatus(masked.id, ReviewStatus.MASKED)
        }

        val body = getMyReviews(buyerId, tab = "written")

        assertEquals(
            listOf("블라인드 처리된 리뷰입니다."),
            JsonPath.read<List<String>>(body, "$.data.items[*].content"),
            "대체 문구가 없으면 원문 대신 블라인드 문구를 내려야 한다",
        )
    }

    @Test
    fun `HIDDEN 리뷰도 목록에 남겨 본인이 상태를 확인하고 지울 수 있게 한다`() {
        val buyerId = createActiveUser()
        val fixture = persistOrder(buyerId, productName = "숨김", confirmedAt = LocalDateTime.now())
        tx.execute {
            val hidden = persistReview(fixture, buyerId, content = "숨겨진 원문")
            em.flush()
            setReviewStatus(hidden.id, ReviewStatus.HIDDEN)
        }

        val body = getMyReviews(buyerId, tab = "written")

        assertEquals(listOf("숨겨진 원문"), JsonPath.read<List<String>>(body, "$.data.items[*].content"), "HIDDEN도 본인에게는 보여야 한다")
        assertEquals(listOf("HIDDEN"), JsonPath.read<List<String>>(body, "$.data.items[*].reviewStatus"), "상태를 그대로 내려야 한다")
    }

    @Test
    fun `삭제한 리뷰는 written 목록에서 빠진다`() {
        val buyerId = createActiveUser()
        val kept = persistOrder(buyerId, productName = "남는 리뷰", confirmedAt = LocalDateTime.now())
        val removed = persistOrder(buyerId, productName = "지운 리뷰", confirmedAt = LocalDateTime.now())
        tx.execute {
            persistReview(kept, buyerId, content = "남는 리뷰입니다")
            val deleted = persistReview(removed, buyerId, content = "지운 리뷰입니다")
            em.flush()
            setReviewStatus(deleted.id, ReviewStatus.DELETED)
        }

        val body = getMyReviews(buyerId, tab = "written")

        assertEquals(listOf("남는 리뷰입니다"), JsonPath.read<List<String>>(body, "$.data.items[*].content"), "삭제한 리뷰는 목록에서 빠져야 한다")
    }

    @Test
    fun `남이 쓴 리뷰는 written 목록에 나오지 않는다`() {
        val owner = createActiveUser()
        val stranger = createActiveUser()
        val fixture = persistOrder(owner, productName = "사료", confirmedAt = LocalDateTime.now())
        tx.execute { persistReview(fixture, owner) }

        val body = getMyReviews(stranger, tab = "written")

        assertEquals(emptyList<String>(), JsonPath.read<List<String>>(body, "$.data.items[*].content"), "남의 리뷰가 보이면 안 된다")
    }

    @Test
    fun `written 커서로 순회하면 모든 리뷰를 한 번씩 지나간다`() {
        val buyerId = createActiveUser()
        val fixture = persistOrder(buyerId, productName = "다품목", confirmedAt = LocalDateTime.now(), itemCount = 3)
        tx.execute { fixture.orderItemIds.forEachIndexed { i, _ -> persistReview(fixture, buyerId, itemIndex = i, content = "리뷰$i") } }

        val page1 = getMyReviews(buyerId, tab = "written", size = 2)
        assertTrue(JsonPath.read<Boolean>(page1, "$.data.hasNext"), "첫 페이지에는 다음이 있어야 한다")
        val page2 = getMyReviews(buyerId, tab = "written", size = 2, cursor = JsonPath.read(page1, "$.data.nextCursor"))
        assertFalse(JsonPath.read<Boolean>(page2, "$.data.hasNext"), "마지막 페이지에는 다음이 없어야 한다")

        val seen =
            JsonPath.read<List<String>>(page1, "$.data.items[*].content") +
                JsonPath.read<List<String>>(page2, "$.data.items[*].content")
        assertEquals(listOf("리뷰2", "리뷰1", "리뷰0"), seen, "커서 순회가 모든 리뷰를 한 번씩 지나가야 한다")
    }

    @Test
    fun `다른 탭에서 받은 커서는 400으로 거절한다`() {
        val buyerId = createActiveUser()
        val fixture = persistOrder(buyerId, productName = "사료", confirmedAt = LocalDateTime.now(), itemCount = 2)
        tx.execute { fixture.orderItemIds.forEachIndexed { i, _ -> persistReview(fixture, buyerId, itemIndex = i) } }

        val writtenCursor = JsonPath.read<String>(getMyReviews(buyerId, tab = "written", size = 1), "$.data.nextCursor")

        performGet(buyerId, tab = "unreviewed", cursor = writtenCursor).andExpect(status().isBadRequest)
    }

    @Test
    fun `깨진 커서와 범위를 벗어난 size와 없는 탭은 400으로 거절한다`() {
        val buyerId = createActiveUser()

        performGet(buyerId, tab = "written", cursor = "%%%broken%%%").andExpect(status().isBadRequest)
        performGet(buyerId, tab = "written", size = 0).andExpect(status().isBadRequest)
        performGet(buyerId, tab = "written", size = 101).andExpect(status().isBadRequest)
        performGet(buyerId, tab = "unknown").andExpect(status().isBadRequest)
        performGet(buyerId, tab = null).andExpect(status().isBadRequest)
    }

    @Test
    fun `토큰이 없으면 401을 준다`() {
        mockMvc.perform(get(PATH).param("tab", "written")).andExpect(status().isUnauthorized)
    }

    private fun performGet(
        userId: Long,
        tab: String?,
        cursor: String? = null,
        size: Int? = null,
    ) = mockMvc.perform(
        get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(userId)).apply {
            tab?.let { param("tab", it) }
            cursor?.let { param("cursor", it) }
            size?.let { param("size", it.toString()) }
        },
    )

    private fun getMyReviews(
        userId: Long,
        tab: String,
        cursor: String? = null,
        size: Int? = null,
    ): String =
        performGet(userId, tab, cursor, size)
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun readOrderItemIds(body: String): List<Long> =
        JsonPath.read<List<Int>>(body, "$.data.items[*].orderItemId").map { it.toLong() }

    private fun bearer(userId: Long) = "Bearer ${mintAccessToken(userId)}"

    /** 구매확정 주문 저장, confirmedAt이 null이면 결제대기 상태로 유지 */
    private fun persistOrder(
        buyerUserId: Long,
        productName: String,
        confirmedAt: LocalDateTime?,
        itemCount: Int = 1,
    ): OrderFixture =
        tx.execute {
            val product = persistProduct(productName)
            em.flush()
            val version = persistProductVersion(product, productName)
            val combinations = (1..itemCount).map { persistOptionCombination(product, it) }
            em.flush()

            val group =
                OrderGroup.create(
                    buyerId = buyerUserId,
                    deliveryAddressId = 0L,
                    deliveryAddress = deliveryAddressSnapshot(),
                    usedPoint = 0L,
                    totalProductAmount = 10000L,
                    totalShippingFee = 3000L,
                    idempotencyKey = "idem-${UUID.randomUUID()}",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )
            em.persist(group)
            val items =
                combinations.map {
                    OrderItem.of(
                        productId = product.id,
                        optionCombinationId = it.id,
                        optionNameSnapshot = it.name,
                        quantity = 1,
                        unitPriceSnapshot = 10000L,
                        discountAllocatedAmount = 0L,
                        productVersionId = version.id,
                    )
                }
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = sellerId,
                    sellerNameSnapshot = "store-$sellerId",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = 3000L,
                    items = items,
                )
            em.persist(order)
            em.flush()

            if (confirmedAt != null) {
                em
                    .createQuery("update Order o set o.status = :st, o.purchaseConfirmedAt = :at where o.id = :id")
                    .setParameter("st", OrderStatus.PURCHASE_CONFIRMED)
                    .setParameter("at", confirmedAt)
                    .setParameter("id", order.id)
                    .executeUpdate()
            }
            OrderFixture(productId = product.id, productPublicId = product.publicId, orderItemIds = items.map { it.id })
        }!!

    private fun persistProduct(name: String): Product {
        val root = Category.create(null, 1, "강아지", null, 1)
        em.persist(root)
        val mid = Category.create(root, 2, "사료간식", null, 1)
        em.persist(mid)
        if (em.find(Seller::class.java, sellerId) == null) {
            em.persist(Seller.open(userId = sellerId, storeName = "store-$sellerId", baseShippingFee = 3000L))
        }
        val product =
            Product.register(
                category = mid,
                sellerId = sellerId,
                name = name,
                description = null,
                representativeImageKey = "products/$name.jpg",
                regularPrice = 10000L,
                discountPrice = null,
                discountStartAt = null,
                discountEndAt = null,
            )
        em.persist(product)
        return product
    }

    private fun persistProductVersion(
        product: Product,
        productName: String,
    ): ProductVersion {
        val version =
            ProductVersion.snapshot(
                product = product,
                versionNo = 1,
                nameSnapshot = "$productName v1",
                priceSnapshot = 10000L,
                statusSnapshot = SaleStatus.ON_SALE,
                thumbnailKeySnapshot = "products/$productName-v1.jpg",
                changedBy = VersionChangedBy.SELLER,
            )
        em.persist(version)
        return version
    }

    private fun persistOptionCombination(
        product: Product,
        index: Int,
    ): OptionCombination {
        val combination =
            OptionCombination.create(
                product = product,
                name = if (index == 1) "블랙 / L" else "블랙 / L$index",
                additionalPrice = 0L,
                stockQuantity = 100,
                valueSignature = "sig-${product.id}-$index",
            )
        em.persist(combination)
        return combination
    }

    private fun persistReview(
        fixture: OrderFixture,
        authorUserId: Long,
        itemIndex: Int = 0,
        content: String = "좋은 상품입니다",
        images: List<String> = emptyList(),
    ): Review {
        val review =
            Review.write(
                productId = fixture.productId,
                optionNameSnapshot = "블랙 / L",
                orderItemId = fixture.orderItemIds[itemIndex],
                authorUserId = authorUserId,
                rating = 5,
                content = content,
                images = images.mapIndexed { i, key -> ReviewImage.of(key, i) },
            )
        em.persist(review)
        return review
    }

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

    private fun deliveryAddressSnapshot() =
        DeliveryAddressSnapshot(
            receiverNameEnc = "enc-receiver",
            contactNumberEnc = "enc-contact",
            zipCode = "12345",
            baseAddress = "서울시 애착구 멍냥로 1",
            detailAddress = null,
            deliveryMemo = null,
        )

    private data class OrderFixture(
        val productId: Long,
        val productPublicId: String,
        val orderItemIds: List<Long>,
    )

    private companion object {
        const val PATH = "/api/v1/users/me/reviews"
    }
}
