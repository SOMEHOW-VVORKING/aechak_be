package com.aechak.api.auth.config

import com.aechak.application.auth.service.WebLoginPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 웹(서버 콜백) 로그인 정책 조립 — yml 값을 application의 정책 값 객체로 공급한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebLoginProperties::class)
class WebLoginConfig {
    @Bean
    fun webLoginPolicy(props: WebLoginProperties): WebLoginPolicy =
        WebLoginPolicy(
            allowedReturnUrls = props.returnUrls.toSet(),
            callbackBaseUrl = props.callbackBaseUrl,
            stateTtl = props.stateTtl,
        )
}
