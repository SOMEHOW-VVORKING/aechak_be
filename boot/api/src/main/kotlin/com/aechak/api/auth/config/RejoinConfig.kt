package com.aechak.api.auth.config

import com.aechak.application.auth.service.RejoinPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RejoinProperties::class)
class RejoinConfig {
    @Bean
    fun rejoinPolicy(props: RejoinProperties): RejoinPolicy = RejoinPolicy(blockedPeriod = props.blockedPeriod)
}
