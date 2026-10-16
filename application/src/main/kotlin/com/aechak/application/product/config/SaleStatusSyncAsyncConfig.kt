package com.aechak.application.product.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

/** 품절 자동 전환 리스너 전용 실행기. 이름으로 지목해 쓰므로 다른 비동기 작업이 생겨도 풀을 나눠 쓰지 않음. */
@Configuration
@EnableAsync
class SaleStatusSyncAsyncConfig {
    @Bean(EXECUTOR_NAME)
    fun saleStatusSyncExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = CORE_SIZE
            maxPoolSize = MAX_SIZE
            setQueueCapacity(QUEUE_CAPACITY)
            setThreadNamePrefix(THREAD_NAME_PREFIX)
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS)
        }

    companion object {
        const val EXECUTOR_NAME = "saleStatusSyncExecutor"

        /** 커넥션 풀이 10이라 전환이 둘까지만 점유하고 나머지는 요청 처리에 남김 */
        private const val CORE_SIZE = 2
        private const val MAX_SIZE = 2

        /** 전환 하나가 쿼리 셋에 3ms 안팎이라 둘이 약 450ms에 비움 */
        private const val QUEUE_CAPACITY = 300

        /**
         * 배포로 내려갈 때 큐에 남은 전환이 사라지면 판매 상태가 어긋난 채 남음.
         * 웹 정리 20초가 먼저 끝난 뒤에 이만큼 더 기다리므로, 둘을 더한 값이 ECS 기본 정지 한도 30초를 넘으면 안 됨.
         */
        private const val AWAIT_TERMINATION_SECONDS = 5

        private const val THREAD_NAME_PREFIX = "sale-status-sync-"
    }
}
