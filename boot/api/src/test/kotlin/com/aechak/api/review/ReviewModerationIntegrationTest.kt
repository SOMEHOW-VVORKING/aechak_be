package com.aechak.api.review

import com.aechak.api.support.KafkaIntegrationTestBase
import com.aechak.application.messaging.MessagePublisher
import com.aechak.domain.review.review.Review
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.message.review.ReviewCreatedMessage
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.test.context.EmbeddedKafka
import java.time.Duration

/**
 * 리뷰 모더레이션 통합 테스트. 리뷰를 직접 적재하고 ReviewCreatedMessage를 발행해,
 * 모더레이션 컨슈머가 마스킹·차단·유지로 전이시키는지 검증한다.
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
class ReviewModerationIntegrationTest : KafkaIntegrationTestBase() {
    @Autowired
    private lateinit var publisher: MessagePublisher

    @PersistenceContext
    private lateinit var em: EntityManager

    @Test
    fun `비속어가 섞이면 MASKED로 전이하고 별표 문구를 남긴다`() {
        val userId = createActiveUser()
        val productId = 501L
        val reviewId = persistPublicReview(productId, userId, "시발 배송은 좋아요")

        publishWritten(reviewId, productId, userId)

        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertEquals("MASKED", reviewStatus(reviewId))
            assertEquals("** 배송은 좋아요", displayContent(reviewId))
        }
        // 마스킹은 노출·집계 대상이라 차단 이벤트를 내지 않는다.
        assertEquals(0L, blockedOutboxCount(reviewId))
    }

    @Test
    fun `비속어 비율이 절반을 넘으면 BLOCKED로 전이하고 평점 집계에서 빠진다`() {
        val userId = createActiveUser()
        val productId = 502L
        val reviewId = persistPublicReview(productId, userId, "시발 씨발 좆")

        publishWritten(reviewId, productId, userId)

        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertEquals("BLOCKED", reviewStatus(reviewId))
            // BLOCKED는 비집계라 재계산 후 상품 평점 리뷰 수가 0이 된다.
            val count = statReviewCount(productId)
            assertTrue(count.isPresent && count.get() == 0)
        }
        // 차단 이벤트는 정확히 한 번만 발행된다.
        assertEquals(1L, blockedOutboxCount(reviewId))
    }

    @Test
    fun `깨끗한 리뷰는 모더레이션 뒤에도 PUBLIC을 유지한다`() {
        val userId = createActiveUser()
        val productId = 503L
        val reviewId = persistPublicReview(productId, userId, "배송 빠르고 품질도 좋아요")

        publishWritten(reviewId, productId, userId)

        // 모더레이션 컨슈머가 이 이벤트를 처리했음을 인박스로 확인한 뒤 상태를 본다.
        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertEquals(1L, moderationProcessedCount(reviewId))
            assertEquals("PUBLIC", reviewStatus(reviewId))
        }
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

    private fun persistPublicReview(
        productId: Long,
        authorUserId: Long,
        content: String,
    ): Long =
        tx.execute {
            val review =
                Review.write(
                    productId = productId,
                    optionNameSnapshot = "블랙 / L",
                    orderItemId = productId,
                    authorUserId = authorUserId,
                    rating = 5,
                    content = content,
                )
            em.persist(review)
            em.flush()
            review.id
        }!!

    private fun publishWritten(
        reviewId: Long,
        productId: Long,
        buyerUserId: Long,
    ) {
        tx.execute {
            publisher.publish(
                ReviewCreatedMessage(reviewId = reviewId, productId = productId, buyerUserId = buyerUserId, hasPhoto = false),
            )
        }
    }

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

    private fun moderationProcessedCount(reviewId: Long): Long =
        db
            .sql("select count(*) from processed_message where consumer = :c and event_id = :e")
            .param("c", "review-moderation")
            .param("e", "review-$reviewId:created")
            .query(Long::class.javaObjectType)
            .single()

    private fun statReviewCount(productId: Long): java.util.Optional<Int> =
        db
            .sql("select review_count from product_stats where product_id = :id")
            .param("id", productId)
            .query(Int::class.javaObjectType)
            .optional()

    private fun blockedOutboxCount(reviewId: Long): Long =
        db
            .sql("select count(*) from outbox_message where event_id = :e")
            .param("e", "review-$reviewId:blocked")
            .query(Long::class.javaObjectType)
            .single()
}
