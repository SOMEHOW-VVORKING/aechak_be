package com.aechak.infra.client.sms

import com.solapi.sdk.message.service.DefaultMessageService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 실발송 배선 — dev·staging·prod 전용. 그 외 프로필은 TestSmsSender가 맡는다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev | staging | prod")
@EnableConfigurationProperties(CoolSmsProperties::class)
class CoolSmsConfig {
    /**
     * 발송 전용 풀 = 동시 발송 상한(격벽). 큐를 두지 않는다 — 대기시킨 발송은 호출자가 포기한 뒤
     * 실행돼 이미 소각된 코드를 내보낼 수 있다. 포화 시 즉시 거절(Abort)로 실패를 드러낸다.
     * 데몬 스레드 + shutdownNow — 최대 50초짜리 좀비 호출이 앱 종료를 붙잡지 못하게 한다.
     */
    @Bean(destroyMethod = "shutdownNow")
    fun coolSmsSendExecutor(): ExecutorService {
        val threadNumber = AtomicInteger(1)
        return ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, SynchronousQueue()) { task ->
            Thread(task, "coolsms-send-${threadNumber.getAndIncrement()}").apply { isDaemon = true }
        }
    }

    @Bean
    fun coolSmsSender(
        properties: CoolSmsProperties,
        coolSmsSendExecutor: ExecutorService,
    ): CoolSmsSender =
        CoolSmsSender(
            DefaultMessageService(properties.apiKey, properties.apiSecret, properties.domain),
            properties.from,
            coolSmsSendExecutor,
            properties.sendTimeout,
        )
}
