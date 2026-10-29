package com.aechak.application.review.summary.request.service

import com.aechak.application.review.summary.port.view.SummaryAspectView
import com.aechak.application.review.summary.port.view.SummaryReviewStateView
import com.aechak.domain.review.summary.ReviewSummary
import com.aechak.domain.review.summary.enums.AspectPolarity
import com.aechak.message.review.SummaryPolarity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [단위] 상품 하나에 무엇을 요청할지 정하는 규칙을 고정한다.
 * 깨지면 AI 서버가 분모를 거짓으로 세거나(미추출 수), 같은 범위에 모델 비용을 되풀이하거나(지문),
 * 바뀐 묶음 요약이 영원히 갱신되지 않는다.
 */
class ReviewSummaryRequestPlannerTest {
    private val product = ReviewSummary.PRODUCT_SCOPE_KEY
    private val smallDog = "DOG:DOG_UNDER_5"
    private val cat = "CAT:CAT_4_6"

    @Test
    fun `추출 안 된 리뷰가 있으면 오래된 순 40건과 상품 전체 범위만 보낸다`() {
        val reviews = (1L..50L).map { review(it, settled = false, extracted = false, segmentKey = smallDog) }

        val plan = assertIs<ReviewSummaryRequestPlan.Send>(ReviewSummaryRequestPlanner.plan(reviews, emptyMap()))

        assertEquals((1L..40L).toList(), plan.newReviewIds)
        val scope = plan.scopes.single()
        assertEquals(product, scope.scopeKey, "묶음 집계에는 이번 새 리뷰가 더해지지 않으므로 추출이 끝난 뒤 보낸다")
        assertEquals(50, scope.basisReviewCount)
        assertEquals(10, scope.unextractedReviewCount, "이번에 보내는 40건은 미추출 수에서 빼야 같은 리뷰가 두 번 빠지지 않는다")
        assertEquals("50:1275:51:40", scope.expectedSignature, "보낸 리뷰가 모두 확정되면 될 지문")
    }

    @Test
    fun `실패 상한에 걸려 확정된 미추출 리뷰는 다시 보내지 않고 분모에서 미추출로 센다`() {
        val reviews = (1L..10L).map { review(it, settled = true, extracted = it != 3L) }

        val plan = assertIs<ReviewSummaryRequestPlan.Send>(ReviewSummaryRequestPlanner.plan(reviews, emptyMap()))

        assertEquals(emptyList(), plan.newReviewIds)
        assertEquals(1, plan.scopes.single().unextractedReviewCount)
    }

    @Test
    fun `추출이 끝나면 지문이 바뀐 범위만 상품 전체부터 두 개까지 보낸다`() {
        val reviews =
            (1L..8L).map { review(it, segmentKey = smallDog) } +
                (9L..16L).map { review(it, segmentKey = cat) } +
                (17L..20L).map { review(it, segmentKey = "DOG:DOG_25_PLUS") }

        val plan = assertIs<ReviewSummaryRequestPlan.Send>(ReviewSummaryRequestPlanner.plan(reviews, emptyMap()))

        assertEquals(listOf(product, cat), plan.scopes.map { it.scopeKey }, "8건 미만 묶음은 요약 기준에 못 미쳐 보내지 않는다")
        assertEquals(8, plan.scopes[1].basisReviewCount)
    }

    @Test
    fun `모든 범위의 지문이 같으면 보내지 않고 상품 지문을 돌려준다`() {
        val reviews = (1L..8L).map { review(it, segmentKey = smallDog) } + (9L..10L).map { review(it) }
        val signatures = mapOf(product to "10:55:11:10", smallDog to "8:36:8:8")

        val plan = assertIs<ReviewSummaryRequestPlan.UpToDate>(ReviewSummaryRequestPlanner.plan(reviews, signatures))

        assertEquals("10:55:11:10", plan.productSignature)
    }

    @Test
    fun `요약이 있던 묶음은 리뷰가 기준 아래로 줄어도 다시 보낸다`() {
        val reviews = (1L..7L).map { review(it, segmentKey = smallDog) } + (8L..10L).map { review(it) }
        val signatures = mapOf(product to "10:55:11:10", smallDog to "8:36:8:8")

        val plan = assertIs<ReviewSummaryRequestPlan.Send>(ReviewSummaryRequestPlanner.plan(reviews, signatures))

        assertEquals(listOf(smallDog), plan.scopes.map { it.scopeKey }, "AI 서버가 리뷰 부족으로 답해야 화면에서 내려간다")
        assertEquals(7, plan.scopes.single().basisReviewCount)
    }

    @Test
    fun `집계는 리뷰 단위로 세고 표본은 최근 리뷰부터 셋까지만 싣는다`() {
        val aspects =
            listOf(
                aspect(1, "PALATABILITY", AspectPolarity.PRO, "기호성", "잘 먹어요"),
                aspect(1, "PALATABILITY", AspectPolarity.PRO, "기호성", "또 잘 먹어요"),
                aspect(2, "PALATABILITY", AspectPolarity.PRO, "먹성", "환장해요"),
                aspect(3, "PALATABILITY", AspectPolarity.PRO, "기호성", "잘 먹음"),
                aspect(4, "PALATABILITY", AspectPolarity.PRO, "식욕", "싹 비워요"),
                aspect(5, "SIZE_FIT", AspectPolarity.CON, "알갱이 크기", "알이 커요"),
            )

        val tally = ReviewSummaryRequestPlanner.tallyOf(aspects)

        val palatability = tally.first()
        assertEquals("PALATABILITY", palatability.aspectCode)
        assertEquals(SummaryPolarity.PRO, palatability.polarity)
        assertEquals(4, palatability.mentionCount, "한 리뷰가 같은 장점을 두 번 말해도 한 번으로 센다")
        assertEquals(listOf("식욕", "기호성", "먹성"), palatability.labelSamples)
        assertEquals(listOf(4L, 3L, 2L), palatability.quoteSamples.map { it.reviewId })
        assertEquals("SIZE_FIT", tally[1].aspectCode)
    }

    private fun review(
        reviewId: Long,
        settled: Boolean = true,
        extracted: Boolean = true,
        segmentKey: String? = null,
    ) = SummaryReviewStateView(reviewId, rating = 5, segmentKey = segmentKey, extracted = extracted, settled = settled)

    private fun aspect(
        reviewId: Long,
        code: String,
        polarity: AspectPolarity,
        label: String,
        quote: String,
    ) = SummaryAspectView(reviewId, null, code, polarity, label, quote)
}
