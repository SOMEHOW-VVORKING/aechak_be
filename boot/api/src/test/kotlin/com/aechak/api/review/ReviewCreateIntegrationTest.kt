package com.aechak.api.review

import com.aechak.api.support.KafkaIntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.user.point.policy.ReviewRewardPolicy
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.websecurity.config.JwtConfig
import com.jayway.jsonpath.JsonPath
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

/**
 * 리뷰 작성 API(POST /reviews) 통합 테스트.
 * 아웃박스 발행을 타므로 Flyway가 켜진 KafkaIntegrationTestBase를 상속한다.
 * REVIEW 토픽을 선언해 컨슈머(평점 투영, 적립)까지 도는 e2e도 함께 검증한다.
 */
@EmbeddedKafka(
    partitions = 1,
    topics = [
        com.aechak.infra.kafka.Topics.ORDER,
        com.aechak.infra.kafka.Topics.ORDER_DLT,
        com.aechak.infra.kafka.Topics.REVIEW,
        com.aechak.infra.kafka.Topics.REVIEW_DLT,
    ],
)
class ReviewCreateIntegrationTest : KafkaIntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var jwtEncoder: JwtEncoder

    @PersistenceContext
    private lateinit var em: EntityManager

    private lateinit var mockMvc: MockMvc
    private val sellerId = 77L

    @BeforeEach
    fun setUpMockMvc() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `구매확정 30일 이내 본인 주문에 작성하면 201과 함께 리뷰와 아웃박스 메시지를 남긴다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId)

        val responseBody =
            postReview(buyerId, reviewBody(fixture.orderItemId))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        val reviewId = JsonPath.read<Int>(responseBody, "$.data.reviewId").toLong()
        assertEquals("블랙 / L", JsonPath.read<String>(responseBody, "$.data.optionName"))

        val outboxCount =
            db
                .sql("select count(*) from outbox_message where event_id = :eid")
                .param("eid", "review-$reviewId:created")
                .query(Long::class.javaObjectType)
                .single()
        assertEquals(1L, outboxCount)
        assertEquals("PUBLIC", reviewStatus(reviewId))
        awaitRewardProcessed(reviewId)
    }

    @Test
    fun `금칙어가 섞인 리뷰는 응답 전에 MASKED 상태와 노출 문구를 저장한다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId)

        val responseBody =
            postReview(buyerId, reviewBody(fixture.orderItemId, content = "시발 배송은 좋아요"))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        val reviewId = JsonPath.read<Int>(responseBody, "$.data.reviewId").toLong()
        assertEquals("MASKED", reviewStatus(reviewId))
        assertEquals("** 배송은 좋아요", displayContent(reviewId))
        awaitRewardProcessed(reviewId)
    }

    @Test
    fun `금칙어 비율이 절반을 넘는 리뷰는 응답 전에 BLOCKED 상태가 되고 적립금을 지급하지 않는다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId)

        val responseBody =
            postReview(buyerId, reviewBody(fixture.orderItemId, content = "시발 씨발 좆!!!"))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        val reviewId = JsonPath.read<Int>(responseBody, "$.data.reviewId").toLong()
        assertEquals("BLOCKED", reviewStatus(reviewId))

        awaitRewardProcessed(reviewId)
        assertEquals(0L, rewardCount(reviewId))
        assertEquals(0L, pointBalance(buyerId))
    }

    @Test
    fun `옵션 조합 원본이 삭제되어도 주문 시점 옵션명으로 리뷰를 작성한다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId)

        tx.execute {
            em.remove(em.find(OptionCombination::class.java, fixture.optionCombinationId))
            em.flush()
        }

        val responseBody =
            postReview(buyerId, reviewBody(fixture.orderItemId))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals("블랙 / L", JsonPath.read<String>(responseBody, "$.data.optionName"))
        awaitRewardProcessed(JsonPath.read<Int>(responseBody, "$.data.reviewId").toLong())
    }

    @Test
    fun `포토 리뷰를 작성하면 컨슈머가 평점을 집계하고 정책 금액을 적립한다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId)
        val tmpKey = "${FileKey.tmpPrefixOf(buyerId, UploadPurpose.REVIEW_IMAGE)}photo.jpg"

        val responseBody =
            postReview(buyerId, reviewBody(fixture.orderItemId, rating = 5, imageKeys = listOf(tmpKey)))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        val reviewId = JsonPath.read<Int>(responseBody, "$.data.reviewId").toLong()
        assertEquals(1, JsonPath.read<List<*>>(responseBody, "$.data.images").size)

        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val reviewCount =
                db
                    .sql("select review_count from product_stats where product_id = :pid")
                    .param("pid", fixture.productId)
                    .query(Int::class.javaObjectType)
                    .optional()
            assertTrue(reviewCount.isPresent && reviewCount.get() == 1)

            val rewardCount =
                db
                    .sql("select count(*) from point_transactions where idempotency_key = :key")
                    .param("key", "EARN:REVIEW_REWARD:$reviewId")
                    .query(Long::class.javaObjectType)
                    .single()
            assertEquals(1L, rewardCount)

            val balance =
                db
                    .sql("select point_balance from users where id = :uid")
                    .param("uid", buyerId)
                    .query(Long::class.javaObjectType)
                    .single()
            assertEquals(ReviewRewardPolicy.amountFor(hasPhoto = true), balance)
        }
    }

    @Test
    fun `남의 주문과 없는 주문은 존재 여부를 숨기기 위해 같은 404를 반환한다`() {
        val ownerId = createActiveUser()
        val attackerId = createActiveUser()
        val fixture = persistReviewableOrderItem(ownerId)

        val notOwnedBody =
            postReview(attackerId, reviewBody(fixture.orderItemId))
                .andExpect(status().isNotFound)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        val missingBody =
            postReview(attackerId, reviewBody(orderItemId = 999999L))
                .andExpect(status().isNotFound)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(110005, JsonPath.read<Int>(notOwnedBody, "$.errorCode"))
        assertEquals(110005, JsonPath.read<Int>(missingBody, "$.errorCode"))
        assertEquals(
            JsonPath.read<String>(missingBody, "$.message"),
            JsonPath.read<String>(notOwnedBody, "$.message"),
        )
    }

    @Test
    fun `구매확정 전 주문이면 400을 반환한다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId, confirmed = false)

        val body =
            postReview(buyerId, reviewBody(fixture.orderItemId))
                .andExpect(status().isBadRequest)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        assertEquals(110007, JsonPath.read<Int>(body, "$.errorCode"))
    }

    @Test
    fun `구매확정 30일이 지나면 400을 반환한다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId, confirmedAt = LocalDateTime.now().minusDays(31))

        val body =
            postReview(buyerId, reviewBody(fixture.orderItemId))
                .andExpect(status().isBadRequest)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        assertEquals(110008, JsonPath.read<Int>(body, "$.errorCode"))
    }

    @Test
    fun `취소·반품한 주문 품목이면 400을 반환한다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId)
        tx.execute {
            em
                .createQuery("update OrderItem oi set oi.itemStatus = :st where oi.id = :id")
                .setParameter("st", OrderItemStatus.CANCELLED)
                .setParameter("id", fixture.orderItemId)
                .executeUpdate()
        }

        val body =
            postReview(buyerId, reviewBody(fixture.orderItemId))
                .andExpect(status().isBadRequest)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        assertEquals(110010, JsonPath.read<Int>(body, "$.errorCode"))
    }

    @Test
    fun `이미 리뷰한 주문 품목에 다시 작성하면 409를 반환한다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId)

        val createdBody =
            postReview(buyerId, reviewBody(fixture.orderItemId))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        val body =
            postReview(buyerId, reviewBody(fixture.orderItemId))
                .andExpect(status().isConflict)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        assertEquals(110004, JsonPath.read<Int>(body, "$.errorCode"))
        awaitRewardProcessed(JsonPath.read<Int>(createdBody, "$.data.reviewId").toLong())
    }

    @Test
    fun `별점이 범위를 벗어나면 400과 도메인 코드를 반환한다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId)

        val body =
            postReview(buyerId, reviewBody(fixture.orderItemId, rating = 6))
                .andExpect(status().isBadRequest)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        assertEquals(110001, JsonPath.read<Int>(body, "$.errorCode"))
    }

    @Test
    fun `사진이 다섯 장을 넘으면 400과 도메인 코드를 반환한다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId)
        val tooMany = (1..6).map { "tmp/$buyerId/reviews/images/p$it.jpg" }

        val body =
            postReview(buyerId, reviewBody(fixture.orderItemId, imageKeys = tooMany))
                .andExpect(status().isBadRequest)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        assertEquals(110009, JsonPath.read<Int>(body, "$.errorCode"))
    }

    @Test
    fun `인증 토큰이 없으면 401을 반환한다`() {
        mockMvc
            .perform(
                post("/api/v1/reviews")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reviewBody(orderItemId = 1L)),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `리뷰를 삭제하면 컨슈머가 평점 집계를 0으로 재계산한다`() {
        val buyerId = createActiveUser()
        val fixture = persistReviewableOrderItem(buyerId)

        val responseBody =
            postReview(buyerId, reviewBody(fixture.orderItemId))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        val reviewId = JsonPath.read<Int>(responseBody, "$.data.reviewId").toLong()
        awaitRewardProcessed(reviewId)

        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val count =
                db
                    .sql("select review_count from product_stats where product_id = :pid")
                    .param("pid", fixture.productId)
                    .query(Int::class.javaObjectType)
                    .optional()
            assertTrue(count.isPresent && count.get() == 1)
        }

        mockMvc
            .perform(delete("/api/v1/reviews/$reviewId").header(HttpHeaders.AUTHORIZATION, bearer(buyerId)))
            .andExpect(status().isNoContent)

        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val count =
                db
                    .sql("select review_count from product_stats where product_id = :pid")
                    .param("pid", fixture.productId)
                    .query(Int::class.javaObjectType)
                    .single()
            assertEquals(0, count)
        }
    }

    private fun postReview(
        userId: Long,
        body: String,
    ) = mockMvc.perform(
        post("/api/v1/reviews")
            .header(HttpHeaders.AUTHORIZATION, bearer(userId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    private fun reviewBody(
        orderItemId: Long,
        rating: Int = 5,
        content: String = "정말 좋은 상품입니다 강력 추천합니다",
        imageKeys: List<String> = emptyList(),
    ): String {
        val keys = imageKeys.joinToString(",") { "\"$it\"" }
        return """{"orderItemId":$orderItemId,"rating":$rating,"content":"$content","imageKeys":[$keys]}"""
    }

    private fun bearer(userId: Long) = "Bearer ${mintAccessToken(userId)}"

    private fun mintAccessToken(userId: Long): String {
        val now = Instant.now()
        val claims =
            JwtClaimsSet
                .builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim(JwtConfig.ROLE_CLAIM, "GENERAL")
                .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    private fun createActiveUser(): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            em.flush()
            em
                .createQuery("update User u set u.status = :st where u.id = :id")
                .setParameter("st", UserStatus.ACTIVE)
                .setParameter("id", user.id)
                .executeUpdate()
            user.id
        }!!

    private fun reviewStatus(reviewId: Long): String =
        db
            .sql("select review_status from reviews where id = :id")
            .param("id", reviewId)
            .query(String::class.java)
            .single()

    private fun displayContent(reviewId: Long): String? =
        db
            .sql("select display_content from reviews where id = :id")
            .param("id", reviewId)
            .query(String::class.java)
            .optional()
            .orElse(null)

    /**
     * 적립 컨슈머가 이 리뷰까지 처리하기를 기다린다.
     * 처리가 테이블 정리 뒤로 밀리면 다음 테스트에 적립 행이 남는다.
     */
    private fun awaitRewardProcessed(reviewId: Long) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertEquals(1L, processedCount("review-point-rewarder", "review-$reviewId:created"))
        }
    }

    private fun processedCount(
        consumer: String,
        eventId: String,
    ): Long =
        db
            .sql("select count(*) from processed_message where consumer = :consumer and event_id = :eventId")
            .param("consumer", consumer)
            .param("eventId", eventId)
            .query(Long::class.javaObjectType)
            .single()

    private fun rewardCount(reviewId: Long): Long =
        db
            .sql("select count(*) from point_transactions where idempotency_key = :key")
            .param("key", "EARN:REVIEW_REWARD:$reviewId")
            .query(Long::class.javaObjectType)
            .single()

    private fun pointBalance(userId: Long): Long =
        db
            .sql("select point_balance from users where id = :id")
            .param("id", userId)
            .query(Long::class.javaObjectType)
            .single()

    private fun persistReviewableOrderItem(
        buyerUserId: Long,
        confirmed: Boolean = true,
        confirmedAt: LocalDateTime = LocalDateTime.now(),
    ): Fixture =
        tx.execute {
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
                    name = "사료",
                    description = null,
                    representativeImageKey = "products/사료.jpg",
                    regularPrice = 10000L,
                    discountPrice = null,
                    discountStartAt = null,
                    discountEndAt = null,
                )
            em.persist(product)
            em.flush()
            val optionCombination =
                OptionCombination.create(
                    product = product,
                    name = "블랙 / L",
                    additionalPrice = 0L,
                    stockQuantity = 100,
                    valueSignature = "sig-${product.id}",
                )
            em.persist(optionCombination)
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
            val item =
                OrderItem.of(
                    productId = product.id,
                    optionCombinationId = optionCombination.id,
                    optionNameSnapshot = optionCombination.name,
                    quantity = 1,
                    unitPriceSnapshot = 10000L,
                    discountAllocatedAmount = 0L,
                    productVersionId = 1L,
                )
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = sellerId,
                    sellerNameSnapshot = "store-$sellerId",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = 3000L,
                    items = listOf(item),
                )
            em.persist(order)
            em.flush()
            if (confirmed) {
                em
                    .createQuery("update Order o set o.status = :st, o.purchaseConfirmedAt = :at where o.id = :id")
                    .setParameter("st", OrderStatus.PURCHASE_CONFIRMED)
                    .setParameter("at", confirmedAt)
                    .setParameter("id", order.id)
                    .executeUpdate()
            }
            assertNotNull(item.id)
            Fixture(
                orderItemId = item.id,
                productId = product.id,
                optionCombinationId = optionCombination.id,
            )
        }!!

    private fun deliveryAddressSnapshot() =
        DeliveryAddressSnapshot(
            receiverNameEnc = "enc-receiver",
            contactNumberEnc = "enc-contact",
            zipCode = "12345",
            baseAddress = "서울시 애착구 멍냥로 1",
            detailAddress = null,
            deliveryMemo = null,
        )

    private data class Fixture(
        val orderItemId: Long,
        val productId: Long,
        val optionCombinationId: Long,
    )
}
