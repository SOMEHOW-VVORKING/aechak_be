package com.aechak.infra.client.sms

import com.aechak.application.user.verification.port.SmsSender
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import kotlin.test.Test

/**
 * 계약 — 테스트 어댑터는 비운영 프로필에서만 뜬다. 깨지면 운영이 발송 없이 조용히 통과하거나,
 * 실벤더 어댑터 없이 운영이 부팅되는 걸 막는 기동 실패 장치가 무력화된다.
 */
class TestSmsSenderProfileTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(SmsClientScan::class.java)

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("com.aechak.infra.client.sms")
    class SmsClientScan

    @Test
    fun `비운영 프로필에서는 TestSmsSender가 SmsSender 빈으로 뜬다`() {
        runner.withPropertyValues("spring.profiles.active=local").run { ctx ->
            assertThat(ctx).hasSingleBean(SmsSender::class.java)
            assertThat(ctx).hasSingleBean(TestSmsSender::class.java)
        }
    }

    @Test
    fun `prod 프로필에는 SmsSender 빈이 없다 — 실벤더 어댑터 등록 전까지 기동 실패를 강제한다`() {
        runner.withPropertyValues("spring.profiles.active=prod").run { ctx ->
            assertThat(ctx).doesNotHaveBean(SmsSender::class.java)
        }
    }
}
