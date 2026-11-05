package com.aechak.infra.kafka.contract

import com.aechak.domain.review.summary.PetWeightBand
import com.aechak.domain.user.pet.enums.Species
import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.config.MessagingJacksonConfig
import com.aechak.message.review.ReviewSummaryMessage
import com.aechak.message.review.ReviewSummaryRequestedMessage
import com.aechak.message.review.SummaryPolarity
import com.aechak.message.review.SummaryQuoteSample
import com.aechak.message.review.SummaryScope
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [단위] 파이썬 AI 서버와 공유하는 리뷰 요약 계약을 고정한다.
 *
 * review-summary-requested.json -> 백엔드가 보내는 모양. AI 서버 ReviewSummaryRequest로 읽히는지 확인한 표본
 * review-summary-result.json -> AI 서버 ReviewSummaryMessage가 직렬화한 표본
 * review-selection-vectors.json -> AI 서버 tests/contract의 체급 묶음 골든 벡터 사본
 * 깨지면 계약이 바뀐 것이므로 AI 서버 쪽과 같은 커밋에서 함께 고쳐야 한다.
 */
class ReviewSummaryContractTest {
    private val mapper: ObjectMapper = MessagingJacksonConfig().messagingObjectMapper()

    private val request =
        ReviewSummaryRequestedMessage(
            requestId = "0b6c1f0e-3f7e-4d8a-9a51-6f2f6c7d2a10",
            productId = 42L,
            categoryPath = listOf("강아지", "사료", "건식사료"),
            totalVisibleReviews = 12,
            ratingBuckets = (1..5).map { ReviewSummaryRequestedMessage.RatingBucket(it, if (it == 5) 12 else 0) },
            newReviews =
                listOf(
                    ReviewSummaryRequestedMessage.NewReview(
                        reviewId = 101L,
                        rating = 5,
                        content = "허겁지겁 먹어요. 알이 좀 커요",
                        optionName = "2kg",
                        hasPhoto = true,
                        createdAt = Instant.parse("2026-10-27T09:00:00Z"),
                    ),
                ),
            requestedScopes =
                listOf(
                    ReviewSummaryRequestedMessage.RequestedScope(
                        scope = SummaryScope.PRODUCT,
                        segmentKey = null,
                        basisReviewCount = 12,
                        unextractedReviewCount = 1,
                        tally =
                            listOf(
                                ReviewSummaryRequestedMessage.TallyRow(
                                    aspectCode = "PALATABILITY",
                                    polarity = SummaryPolarity.PRO,
                                    mentionCount = 6,
                                    labelSamples = listOf("기호성"),
                                    quoteSamples = listOf(SummaryQuoteSample(99L, "잘 먹어요")),
                                ),
                            ),
                    ),
                ),
            occurredAt = Instant.parse("2026-10-28T03:00:00Z"),
        )

    @Test
    fun `리뷰 요약 요청의 직렬화 모양이 고정된다`() {
        assertEquals(mapper.readTree(readFixture("review-summary-requested.json")), mapper.readTree(mapper.writeValueAsString(request)))
    }

    @Test
    fun `리뷰 요약 요청은 AI 서버가 정한 요청 토픽으로 간다`() {
        assertEquals("aechak.summary.requests", Topics.of(request.aggregateType))
        assertEquals("aechak.summary.results", Topics.REVIEW_SUMMARY_RESULT)
    }

    @Test
    fun `AI 서버가 직렬화한 결과를 백엔드가 읽는다`() {
        val result = mapper.readValue(readFixture("review-summary-result.json"), ReviewSummaryMessage::class.java)

        assertEquals(ReviewSummaryMessage.SCHEMA_VERSION, result.schemaVersion)
        assertEquals(ReviewSummaryMessage.State.OK, result.state)
        val extracted = result.extracted.associateBy { it.reviewId }
        assertEquals(SummaryPolarity.CON, extracted.getValue(101L).aspects[1].polarity)
        assertEquals("INJECTION", extracted.getValue(102L).excludeReason)
        assertNull(extracted.getValue(103L).excludeReason, "사유 없는 미추출은 다시 보낼 대상이다")
        val (product, segment) = result.summaries
        assertEquals(10, product.analyzedReviewCount)
        assertEquals("알갱이가 크다는 의견이 있어요", product.cons.single().headline)
        assertEquals(
            SummaryQuoteSample(101L, "허겁지겁 먹어요"),
            product.pros
                .single()
                .quotes
                .single(),
        )
        assertEquals("DOG:DOG_UNDER_5", segment.segmentKey)
        assertEquals("NOT_ENOUGH_REVIEWS", segment.consAbsenceReason)
    }

    @Test
    fun `체급 묶음 키가 AI 서버 골든 벡터와 같다`() {
        val golden = mapper.readTree(readFixture("review-selection-vectors.json"))

        val goldenSegmentKeys: List<String> = golden.get("segmentKeys").values().map { it.asString() }
        assertEquals(goldenSegmentKeys, PetWeightBand.entries.map { it.segmentKey })
        for (vector in golden["vectors"]) {
            for ((side, expected) in listOf("viewer" to "viewerSegment", "reviewer" to "reviewerSegment")) {
                assertEquals(textOrNull(vector[expected]), segmentKeyOf(vector[side]), "${vector["name"].asString()} $side")
            }
        }
    }

    private fun segmentKeyOf(facts: JsonNode): String? {
        if (facts.isNull) return null
        val weight = facts["weightKg"].takeUnless { it.isNull }?.let { BigDecimal(it.asString()) }
        return PetWeightBand.of(Species.valueOf(facts["species"].asString()), weight)?.segmentKey
    }

    private fun textOrNull(node: JsonNode): String? = node.takeUnless { it.isNull }?.asString()

    private fun readFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/contract/$name")) { name }
            .readAllBytes()
            .decodeToString()
            .trimEnd('\n')
}
