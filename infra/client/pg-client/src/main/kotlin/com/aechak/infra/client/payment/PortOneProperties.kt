package com.aechak.infra.client.payment

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("payment.portone")
data class PortOneProperties(
    val baseUrl: String = "https://api.portone.io",
    val apiSecret: String = "",
)
