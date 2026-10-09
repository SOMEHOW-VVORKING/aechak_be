package com.aechak.api.payment.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("payment.store")
data class PaymentStoreProperties(
    val id: String = "",
    val channelKey: String = "",
)
