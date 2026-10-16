package com.aechak.infra.client.payment.reconciliation

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.util.Base64

/**
 * 토스페이먼츠 직접 연동 설정. 포트원 채널로 토스를 쓰는 환경에서는 가맹점이 토스 키를 들고 있어도
 * 주문번호(orderId)가 우리 결제 식별자와 같다는 보장이 없으므로, 그 계약을 확인한 환경에서만 enabled를 켠다.
 */
@ConfigurationProperties("payment.toss")
data class TossPaymentsProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://api.tosspayments.com",
    /** 시크릿 키(test_sk_/live_sk_). Basic 인증 — 키 뒤에 콜론을 붙여 Base64 */
    val secretKey: String = "",
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}

@Configuration(proxyBeanMethods = false)
@Conditional(TossReconciliationEnabled::class)
@EnableConfigurationProperties(TossPaymentsProperties::class)
class TossPaymentsReconciliationConfig {
    @Bean(name = [TOSS_REST_CLIENT])
    fun tossPaymentsRestClient(properties: TossPaymentsProperties): RestClient =
        RestClient
            .builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth(properties.secretKey))
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT_MS)
                    setReadTimeout(READ_TIMEOUT_MS)
                },
            ).build()

    @Bean
    fun tossPaymentsReconciliationSource(
        tossPaymentsRestClient: RestClient,
        properties: TossPaymentsProperties,
    ): TossPaymentsReconciliationSource = TossPaymentsReconciliationSource(tossPaymentsRestClient, properties)

    companion object {
        const val TOSS_REST_CLIENT = "tossPaymentsRestClient"
        private const val CONNECT_TIMEOUT_MS = 2_000
        private const val READ_TIMEOUT_MS = 60_000

        fun basicAuth(secretKey: String): String = "Basic " + Base64.getEncoder().encodeToString("$secretKey:".toByteArray())
    }
}
