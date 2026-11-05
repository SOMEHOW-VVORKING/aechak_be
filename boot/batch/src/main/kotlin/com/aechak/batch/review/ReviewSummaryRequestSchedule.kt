package com.aechak.batch.review

import com.aechak.application.review.summary.request.usecase.ReviewSummaryRequestUseCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 노출 리뷰가 바뀐 상품에 AI 리뷰 요약을 요청함. 결과는 AI 서버가 결과 토픽으로 돌려줌
 * AI 서버의 모델 호출 비용이 드는 경로라 기본값은 꺼져 있고, 회차당 요청 상품 수에 상한을 둠
 */
@Component
@ConditionalOnProperty(name = ["review.summary.enabled"], havingValue = "true")
class ReviewSummaryRequestSchedule(
    private val reviewSummaryRequestUseCase: ReviewSummaryRequestUseCase,
    @Value("\${review.summary.max-products-per-run:30}") private val maxProductsPerRun: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${review.summary.cron:0 15 * * * *}", zone = "Asia/Seoul")
    fun run() {
        val now = LocalDateTime.now()
        val productIds = reviewSummaryRequestUseCase.findRequestTargetProductIds(now, maxProductsPerRun)
        var requested = 0
        productIds.forEach { productId ->
            try {
                if (reviewSummaryRequestUseCase.requestSummaryIfStale(productId, now)) requested++
            } catch (e: Exception) {
                log.warn("리뷰 요약 요청 실패, 다음 상품으로 넘어감. productId={}", productId, e)
            }
        }
        log.info("리뷰 요약 요청 회차 종료. 대상={}건, 요청={}건", productIds.size, requested)
    }
}
