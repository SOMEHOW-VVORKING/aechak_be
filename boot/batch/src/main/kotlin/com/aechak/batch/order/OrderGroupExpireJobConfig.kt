package com.aechak.batch.order

import com.aechak.application.order.usecase.OrderGroupExpireUseCase
import com.aechak.application.order.usecase.result.ExpireTargetResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.listener.SkipListener
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 스텝 트랜잭션은 기본값(Resourceless)이라 커넥션을 안 잡음. 취소는 유스케이스 안 건별 REQUIRES_NEW가 엶.
 * 스텝이 열면 포트원 왕복 동안 커넥션을 쥐기 때문.
 * skip 시 청크 재스캔이 처리 완료 건의 포트원 조회를 다시 부를 수 있으나 DB 효과는 전 구간 멱등이라 무연산
 */
@Configuration(proxyBeanMethods = false)
class OrderGroupExpireJobConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean(ORDER_GROUP_EXPIRE_JOB)
    fun orderGroupExpireJob(
        jobRepository: JobRepository,
        orderGroupExpireStep: Step,
    ): Job =
        JobBuilder(ORDER_GROUP_EXPIRE_JOB, jobRepository)
            .start(orderGroupExpireStep)
            .build()

    @Bean
    fun orderGroupExpireStep(
        jobRepository: JobRepository,
        expireTargetReader: ItemReader<ExpireTargetResult>,
        expireUseCase: OrderGroupExpireUseCase,
    ): Step =
        StepBuilder(ORDER_GROUP_EXPIRE_STEP, jobRepository)
            .chunk<ExpireTargetResult, ExpireTargetResult>(CHUNK)
            .reader(expireTargetReader)
            .processor(
                ItemProcessor { target ->
                    expireUseCase.cancelIfUnpaid(target)
                    target
                },
            ).writer(ItemWriter {})
            .faultTolerant()
            // 포트원 오류 -> 그 건만 skip하고 이어감
            // 그 외 예외 -> 즉시 스텝 실패
            .skipPolicy { t, skipCount ->
                t is BusinessException &&
                    t.errorCode == PaymentErrorCode.PAYMENT_GATEWAY_ERROR &&
                    skipCount < SKIP_LIMIT
            }.skipListener(
                object : SkipListener<ExpireTargetResult, ExpireTargetResult> {
                    override fun onSkipInProcess(
                        item: ExpireTargetResult,
                        t: Throwable,
                    ) {
                        log.error("주문그룹 만료 처리 실패. orderGroupId={}", item.orderGroupId, t)
                    }
                },
            ).build()

    @Bean
    @StepScope
    fun expireTargetReader(expireUseCase: OrderGroupExpireUseCase): ItemReader<ExpireTargetResult> =
        object : ItemReader<ExpireTargetResult> {
            private var cursor: ExpireTargetResult? = null
            private var exhausted = false
            private val buffer = ArrayDeque<ExpireTargetResult>() // 청크 단위로 buffer에 올려두고 개별처리

            override fun read(): ExpireTargetResult? {
                if (buffer.isEmpty() && !exhausted) {
                    val page = expireUseCase.findExpireTargets(cursor, CHUNK)
                    if (page.size < CHUNK) exhausted = true
                    cursor = page.lastOrNull() ?: cursor
                    buffer.addAll(page)
                }
                return buffer.removeFirstOrNull()
            }
        }

    companion object {
        const val ORDER_GROUP_EXPIRE_JOB = "orderGroupExpireJob"
        private const val ORDER_GROUP_EXPIRE_STEP = "orderGroupExpireStep"
        private const val CHUNK = 50
        private const val SKIP_LIMIT = 100L
    }
}
