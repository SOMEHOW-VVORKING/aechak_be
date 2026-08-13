package com.aechak.infra.client.sms

import com.solapi.sdk.message.service.DefaultMessageService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 실발송 배선 — dev·staging·prod 전용. 그 외 프로필은 TestSmsSender가 맡는다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev | staging | prod")
@EnableConfigurationProperties(CoolSmsProperties::class)
class CoolSmsConfig {
    @Bean
    fun coolSmsSender(properties: CoolSmsProperties): CoolSmsSender =
        CoolSmsSender(
            DefaultMessageService(properties.apiKey, properties.apiSecret, properties.domain),
            properties.from,
        )
}
