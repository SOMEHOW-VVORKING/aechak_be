package com.aechak.infra.client.ai

import com.aechak.application.product.draft.port.ProductDraftAutofillPort
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 계약. 초안 어댑터는 **주소와 비밀이 둘 다 있을 때만** 조립된다.
 *
 * 이게 깨지면 둘 중 하나다 — 값이 없는 local·test 부팅이 깨지거나(빈이 생겨 실주소 없이
 * 호출을 시도한다), 값이 다 있는 dev에서 빈이 안 생겨 전건 UNAVAILABLE이 된다.
 */
class AiDraftClientAssemblyTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(AiClientScan::class.java)

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("com.aechak.infra.client.ai")
    class AiClientScan

    @Test
    fun `아무것도 적지 않으면 초안 포트 빈이 조립되지 않는다`() {
        runner.run { context ->
            assertThat(context).doesNotHaveBean(ProductDraftAutofillPort::class.java)
        }
    }

    @Test
    fun `주소만 있으면 조립되지 않는다`() {
        runner.withPropertyValues("${AiDraftClientEnabled.PREFIX}.base-url=http://ai.internal:8200").run { context ->
            assertThat(context).doesNotHaveBean(ProductDraftAutofillPort::class.java)
        }
    }

    @Test
    fun `비밀만 있으면 조립되지 않는다`() {
        runner.withPropertyValues("${AiDraftClientEnabled.PREFIX}.shared-secret=s3cret").run { context ->
            assertThat(context).doesNotHaveBean(ProductDraftAutofillPort::class.java)
        }
    }

    @Test
    fun `주소와 비밀이 둘 다 있으면 어댑터 하나가 조립되고 설정이 바인딩된다`() {
        runner
            .withPropertyValues(
                "${AiDraftClientEnabled.PREFIX}.base-url=http://ai.internal:8200",
                "${AiDraftClientEnabled.PREFIX}.shared-secret=s3cret",
                "${AiDraftClientEnabled.PREFIX}.read-timeout=30s",
            ).run { context ->
                assertThat(context).hasSingleBean(ProductDraftAutofillPort::class.java)
                assertThat(context).hasSingleBean(AiDraftClientAdapter::class.java)
                assertEquals("s3cret", context.getBean(AiDraftProperties::class.java).sharedSecret)
            }
    }
}
