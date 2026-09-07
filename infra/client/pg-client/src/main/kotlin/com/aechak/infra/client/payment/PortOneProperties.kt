package com.aechak.infra.client.payment

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("payment.portone")
data class PortOneProperties(
    val baseUrl: String = "https://api.portone.io",
    val apiSecret: String = "",
    /** 웹훅 서명 검증 키(whsec_ 접두 + Base64). 비면 웹훅을 받지 않는다 */
    val webhookSecret: String = "",
)
