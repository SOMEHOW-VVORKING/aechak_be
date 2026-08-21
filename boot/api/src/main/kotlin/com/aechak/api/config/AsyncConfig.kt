package com.aechak.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {
    @Bean
    fun recentSearchTaskExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 500
            setThreadNamePrefix("recent-search-")
            setRejectedExecutionHandler(ThreadPoolExecutor.DiscardPolicy())
            // 종료 유실 감소를 위해 종료 시 큐에 남은 적재 작업을 최대 10초 기다렸다가 내림
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(10)
            initialize()
        }

    @Bean
    fun inquiryNotificationTaskExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 2
            queueCapacity = 100
            setThreadNamePrefix("inquiry-noti-")
            // 과부하 시 호출 스레드에서 발송하도록 설정(CallerRuns)
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(10)
            initialize()
        }
}
