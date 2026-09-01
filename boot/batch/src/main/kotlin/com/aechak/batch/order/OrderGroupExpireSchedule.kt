package com.aechak.batch.order

import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Lazy(false)
@Component
class OrderGroupExpireSchedule(
    private val jobOperator: JobOperator,
    @Qualifier(OrderGroupExpireJobConfig.ORDER_GROUP_EXPIRE_JOB)
    private val orderGroupExpireJob: Job,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000)
    fun poll() {
        try {
            val execution =
                jobOperator.start(
                    orderGroupExpireJob,
                    JobParametersBuilder()
                        .addLocalDateTime("scheduledAt", LocalDateTime.now()) // 실행마다 새 JobInstance가 되도록 하는 유일 파라미터
                        .toJobParameters(),
                )
            if (execution.status != BatchStatus.COMPLETED) {
                log.error("만료 잡이 정상 종료하지 못함. status={}, exitStatus={}", execution.status, execution.exitStatus)
            }
        } catch (e: Exception) {
            log.error("만료 잡 실행 실패. 다음 주기에 재시도함", e)
        }
    }
}
