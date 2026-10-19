package com.aechak.batch.order.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("order.delivery")
data class OrderDeliveryProperties(
    /** 배치 전체 on/off. 배치 모듈이 배포되기 전까지는 꺼 둔다. */
    val enabled: Boolean = false,
    /** 송장 등록 후 이 일수가 지나도 배송완료가 아니면 집화 지연으로 경고만 남긴다. */
    val dispatchDelayDays: Long = DEFAULT_DISPATCH_DELAY_DAYS,
    /** 송장 등록 후 이 일수가 지나면 배송완료로 자동 전이한다. */
    val autoCompleteDays: Long = DEFAULT_AUTO_COMPLETE_DAYS,
    /** 한 페이지에 읽을 최대 건수. 회차는 커서로 페이지를 넘기며 대상을 모두 처리한다. */
    val scanLimit: Int = DEFAULT_SCAN_LIMIT,
) {
    companion object {
        const val DEFAULT_DISPATCH_DELAY_DAYS = 1L
        const val DEFAULT_AUTO_COMPLETE_DAYS = 4L
        const val DEFAULT_SCAN_LIMIT = 200
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderDeliveryProperties::class)
class OrderDeliveryConfig
