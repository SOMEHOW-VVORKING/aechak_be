package com.aechak.api.review

import com.aechak.api.support.KafkaIntegrationTestBase
import com.aechak.application.review.summary.request.usecase.ReviewSummaryRequestUseCase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.review.review.Review
import com.aechak.domain.seller.seller.Seller
import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.Envelope
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.KafkaTestUtils
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

/**
 * 통합 테스트. 리뷰 AI 요약의 요청 선정과 AI 서버에 보내는 요청 바이트를 실 MySQL과 내장 Kafka로 고정한다.
 * 깨지면 요약이 영원히 안 만들어지거나(선정, 지문), 화면에 안 나오는 상품에 모델 비용을 쓰거나, AI 서버가 요청을 못 읽는다(요청 바이트).
 */
class ReviewSummaryIntegrationTest : KafkaIntegrationTestBase() {
    @PersistenceContext
    private lateinit var em: EntityManager

    @Autowired
    private lateinit var reviewSummaryRequestUseCase: ReviewSummaryRequestUseCase

    @Autowired
    private lateinit var kafka: KafkaTemplate<String, String>

    @Autowired
    @Qualifier(MESSAGING_OBJECT_MAPPER)
    private lateinit var objectMapper: ObjectMapper

    private var nextOrderItemId = 1L

    @Test
    fun `추출 안 된 노출 리뷰가 있으면 그 리뷰의 노출 본문과 상품 전체 범위만 보내고 응답 전까지 다시 고르지 않는다`() {
        val product = persistProduct("추출 대기")
        val reviewIds = persistReviews(product.id, publicCount = 10, maskedCount = 1, blockedCount = 1)
        val now = LocalDateTime.now()

        assertThat(reviewSummaryRequestUseCase.findRequestTargetProductIds(now, 10)).contains(product.id)
        assertThat(reviewSummaryRequestUseCase.requestSummaryIfStale(product.id, now)).isTrue()

        val envelope = pollEnvelope(Topics.REVIEW_SUMMARY_REQUEST, product.id.toString())
        assertThat(envelope.eventType).`as`("AI 서버는 eventType으로만 요청을 고른다").isEqualTo("ReviewSummaryRequestedMessage")
        val payload = objectMapper.readTree(envelope.payload)
        val newReviews = payload["newReviews"]
        assertThat(newReviews.values().map { it["reviewId"].asLong() }).`as`("차단 리뷰는 보내지 않는다").containsExactlyElementsOf(reviewIds.take(11))
        assertThat(newReviews.last()["content"].asString()).`as`("마스킹 리뷰는 대체 문구를 보내야 금칙어가 되살아나지 않는다").isEqualTo(MASKED_DISPLAY)
        val scopes = payload["requestedScopes"]
        assertThat(scopes.values().map { it["scope"].asString() }).`as`("체급 묶음은 추출이 끝난 뒤에 보낸다").containsExactly("PRODUCT")
        assertThat(scopes[0]["basisReviewCount"].asInt()).isEqualTo(11)
        assertThat(scopes[0]["unextractedReviewCount"].asInt()).`as`("이번에 보내는 리뷰는 미추출 수에서 뺀다").isEqualTo(0)

        assertThat(reviewSummaryRequestUseCase.findRequestTargetProductIds(now, 10))
            .`as`("응답을 기다리는 상품을 다시 보내면 같은 리뷰에 모델 비용을 두 번 쓴다")
            .doesNotContain(product.id)
    }

    @Test
    fun `검수에서 반려된 상품은 노출되지 않으므로 요약 대상으로 고르지 않는다`() {
        val product = persistProduct("검수 반려")
        persistReviews(product.id, publicCount = 10)
        db.sql("UPDATE products SET inspection_status = 'REJECTED' WHERE id = :id").param("id", product.id).update()

        assertThat(reviewSummaryRequestUseCase.findRequestTargetProductIds(LocalDateTime.now(), 10)).doesNotContain(product.id)
    }

    @Test
    fun `추출이 끝난 상품은 저장된 장단점으로 집계표를 만들어 상품 전체와 체급 묶음을 보낸다`() {
        val product = persistProduct("집계 요청")
        val reviewIds = persistReviews(product.id, publicCount = 10)
        reviewIds.take(8).forEach { setSegmentKey(it, "DOG:DOG_UNDER_5") }
        reviewIds.forEach { insertExtracted(it, product.id, "PALATABILITY", "PRO", "잘 먹어요 $it") }

        assertThat(reviewSummaryRequestUseCase.requestSummaryIfStale(product.id, LocalDateTime.now())).isTrue()

        val payload = objectMapper.readTree(pollEnvelope(Topics.REVIEW_SUMMARY_REQUEST, product.id.toString()).payload)
        assertThat(payload["newReviews"].size()).isZero()
        val scopes = payload["requestedScopes"]
        assertThat(
            scopes.values().map {
                it["segmentKey"]
                    .takeUnless { node ->
                        node.isNull
                    }?.asString()
            },
        ).containsExactly(null, "DOG:DOG_UNDER_5")
        val productTally = scopes[0]["tally"].single()
        assertThat(productTally["mentionCount"].asInt()).isEqualTo(10)
        assertThat(productTally["quoteSamples"].values().map { it["reviewId"].asLong() })
            .`as`("인용 표본은 최근 리뷰부터 셋")
            .containsExactlyElementsOf(reviewIds.reversed().take(3))
        assertThat(scopes[1]["tally"].single()["mentionCount"].asInt()).`as`("묶음 집계는 그 묶음 리뷰만 센다").isEqualTo(8)
        assertThat(scopes[1]["basisReviewCount"].asInt()).isEqualTo(8)
    }

    // ---------- 픽스처 ----------

    private fun persistProduct(name: String): Product =
        tx.execute {
            if (em.find(Seller::class.java, SELLER_ID) == null) {
                em.persist(Seller.open(userId = SELLER_ID, storeName = "store", baseShippingFee = 3000L))
            }
            val category = Category.create(null, 1, "강아지-$name", null, 1)
            em.persist(category)
            val product =
                Product.register(
                    category = category,
                    sellerId = SELLER_ID,
                    name = name,
                    description = null,
                    representativeImageKey = "products/$name.jpg",
                    regularPrice = 10000L,
                    discountPrice = null,
                    discountStartAt = null,
                    discountEndAt = null,
                )
            em.persist(product)
            product
        }!!

    /** 공개, 마스킹, 차단 순으로 만든다. 반환하는 id도 그 순서다 */
    private fun persistReviews(
        productId: Long,
        publicCount: Int,
        maskedCount: Int = 0,
        blockedCount: Int = 0,
    ): List<Long> =
        tx.execute {
            List(publicCount + maskedCount + blockedCount) { index ->
                val review = newReview(productId)
                when {
                    index >= publicCount + maskedCount -> review.block()
                    index >= publicCount -> review.mask(MASKED_DISPLAY)
                }
                em.persist(review)
                review
            }.also { em.flush() }.map { it.id }
        }!!

    private fun newReview(productId: Long): Review =
        Review.write(
            productId = productId,
            optionNameSnapshot = "기본",
            orderItemId = nextOrderItemId++,
            authorUserId = 1L,
            rating = 5,
            content = "잘 먹어요",
        )

    private fun setSegmentKey(
        reviewId: Long,
        segmentKey: String,
    ) {
        db
            .sql("UPDATE reviews SET pet_segment_key = :key WHERE id = :id")
            .param("key", segmentKey)
            .param("id", reviewId)
            .update()
    }

    private fun insertExtracted(
        reviewId: Long,
        productId: Long,
        aspectCode: String,
        polarity: String,
        quote: String,
    ) {
        db
            .sql(
                """
                INSERT INTO review_aspect_extractions (review_id, product_id, status, failed_attempt_count, extract_version, created_at, updated_at)
                VALUES (:reviewId, :productId, 'EXTRACTED', 0, 'extract-v1', NOW(6), NOW(6))
                """,
            ).param("reviewId", reviewId)
            .param("productId", productId)
            .update()
        db
            .sql(
                """
                INSERT INTO review_aspects (review_id, sort_order, aspect_code, polarity, label, quote)
                VALUES (:reviewId, 0, :aspectCode, :polarity, '기호성', :quote)
                """,
            ).param("reviewId", reviewId)
            .param("aspectCode", aspectCode)
            .param("polarity", polarity)
            .param("quote", quote)
            .update()
    }

    private fun pollEnvelope(
        topic: String,
        key: String,
    ): Envelope {
        val props =
            mapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to appProducerBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG to "review-summary-request-checker-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            )
        DefaultKafkaConsumerFactory<String, String>(props).createConsumer().use { consumer ->
            consumer.subscribe(listOf(topic))
            val deadline = Instant.now().plusSeconds(15)
            while (Instant.now().isBefore(deadline)) {
                val record = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2)).records(topic).lastOrNull { it.key() == key }
                if (record != null) return objectMapper.readValue(record.value(), Envelope::class.java)
            }
        }
        error("$topic 토픽에서 key=$key 레코드를 15초 안에 받지 못했다")
    }

    // 전체 실행에서는 내장 브로커가 컨텍스트마다 떠서 brokers 프로퍼티가 다른 브로커를 가리킬 수 있음. 앱 발행기가 실제로 쓰는 주소를 읽음
    private fun appProducerBootstrapServers(): String =
        when (val servers = kafka.producerFactory.configurationProperties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG]) {
            is Collection<*> -> servers.joinToString(",")
            else -> servers.toString()
        }

    private fun JsonNode.single(): JsonNode {
        check(size() == 1) { "한 건이어야 하는데 ${size()}건: $this" }
        return this[0]
    }

    companion object {
        private const val SELLER_ID = 77L
        private const val MASKED_DISPLAY = "마스킹된 ** 문구"
    }
}
