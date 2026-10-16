package com.aechak.infra.client.sms

import com.aechak.application.user.verification.port.SmsSender
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import kotlin.test.Test

/**
 * 계약 — 실발송 프로필(dev·staging·prod)은 CoolSMS 어댑터, 그 외(local·test·기본)는 발송 생략 어댑터.
 * 깨지면 로컬·테스트가 실과금 발송을 하거나, 실환경이 발송 없이 조용히 통과하거나,
 * 키 미주입 실환경을 기동 실패로 막는 장치가 무력화된다.
 */
class SmsSenderProfileTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(SmsClientScan::class.java)

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("com.aechak.infra.client.sms")
    class SmsClientScan

    @Test
    fun `비실발송 프로필에서는 TestSmsSender가 SmsSender 빈으로 뜬다`() {
        runner.withPropertyValues("spring.profiles.active=local").run { ctx ->
            assertThat(ctx).hasSingleBean(SmsSender::class.java)
            assertThat(ctx).hasSingleBean(TestSmsSender::class.java)
        }
    }

    @Test
    fun `프로필 미지정(통합 테스트 기본)에서도 TestSmsSender가 뜬다`() {
        runner.run { ctx ->
            assertThat(ctx).hasSingleBean(SmsSender::class.java)
            assertThat(ctx).hasSingleBean(TestSmsSender::class.java)
        }
    }

    @Test
    fun `실발송 프로필에서는 CoolSmsSender가 SmsSender 빈으로 뜬다`() {
        runner
            .withPropertyValues(
                "spring.profiles.active=dev",
                "sms.coolsms.api-key=test-key",
                "sms.coolsms.api-secret=test-secret",
                "sms.coolsms.from=0212345678",
            ).run { ctx ->
                assertThat(ctx).hasSingleBean(SmsSender::class.java)
                assertThat(ctx).hasSingleBean(CoolSmsSender::class.java)
            }
    }

    @Test
    fun `발송 executor는 큐 없이 포화 시 즉시 거절한다`() {
        // 큐가 생기면 호출자가 포기한 발송이 뒤늦게 실행돼 소각된 코드가 나간다 — 구성 자체를 계약으로 고정
        runner
            .withPropertyValues(
                "spring.profiles.active=dev",
                "sms.coolsms.api-key=test-key",
                "sms.coolsms.api-secret=test-secret",
                "sms.coolsms.from=0212345678",
            ).run { ctx ->
                val executor = ctx.getBean("coolSmsSendExecutor") as ThreadPoolExecutor
                assertThat(executor.queue).isInstanceOf(SynchronousQueue::class.java)
                assertThat(executor.rejectedExecutionHandler).isInstanceOf(ThreadPoolExecutor.AbortPolicy::class.java)
                assertThat(executor.maximumPoolSize).isEqualTo(4)
            }
    }

    @Test
    fun `실발송 프로필에서 CoolSMS 키 미주입이면 기동에 실패한다`() {
        runner.withPropertyValues("spring.profiles.active=prod").run { ctx ->
            assertThat(ctx).hasFailed()
            // 무관한 이유의 기동 실패로도 통과하지 않게, 실패 원인이 키 바인딩임을 함께 고정
            assertThat(ctx.startupFailure).hasStackTraceContaining("sms.coolsms")
        }
    }
}
