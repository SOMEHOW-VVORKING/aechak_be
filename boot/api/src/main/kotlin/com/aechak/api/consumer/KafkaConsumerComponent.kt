package com.aechak.api.consumer

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
@ConditionalOnProperty("spring.kafka.bootstrap-servers") // 해당 설정 주입이 없으면 빈이 뜨지 않도록 함
annotation class KafkaConsumerComponent
