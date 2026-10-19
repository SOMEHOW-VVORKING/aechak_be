package com.aechak.application.order.delivery.config

import com.aechak.application.order.delivery.service.AutoConfirmPolicy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 구매확정 기한 정책을 설정값으로 조립한다. 셀러 수동 전이와 배치 자동 전이가 같은 빈을 쓴다. */
@Configuration(proxyBeanMethods = false)
class AutoConfirmPolicyConfig {
    @Bean
    fun autoConfirmPolicy(
        @Value("\${order.auto-confirm.days:$DEFAULT_DAYS}") days: Long,
    ): AutoConfirmPolicy = AutoConfirmPolicy(days)

    companion object {
        private const val DEFAULT_DAYS = 5L
    }
}
