package com.aechak.api.payment.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentStoreProperties::class)
class PaymentStoreConfig
