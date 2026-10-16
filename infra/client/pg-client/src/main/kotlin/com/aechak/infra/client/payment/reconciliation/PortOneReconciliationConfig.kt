package com.aechak.infra.client.payment.reconciliation

import com.aechak.infra.client.payment.PortOneProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@ConfigurationProperties("payment.reconciliation.portone")
data class PortOneReconciliationProperties(
    /** null이면 테스트·라이브 구분 없이 전부. dev는 테스트 채널만 쓰므로 true, prod는 false를 권장 */
    val isTest: Boolean? = null,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}

/**
 * 대사용 포트원 클라이언트. 확정 흐름이 쓰는 클라이언트(읽기 10초)와 분리한 이유는
 * 목록 조회가 페이지당 수백 건을 싣고 포트원이 읽기 타임아웃 60초 이상을 권장하기 때문이다.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(PortOneReconciliationEnabled::class)
@EnableConfigurationProperties(PortOneReconciliationProperties::class)
class PortOneReconciliationConfig {
    @Bean(name = [PORT_ONE_RECONCILIATION_REST_CLIENT])
    fun portOneReconciliationRestClient(properties: PortOneProperties): RestClient =
        RestClient
            .builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne ${properties.apiSecret}")
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT_MS)
                    setReadTimeout(READ_TIMEOUT_MS)
                },
            ).build()

    @Bean
    fun portOneReconciliationSource(
        portOneReconciliationRestClient: RestClient,
        portOneProperties: PortOneProperties,
        properties: PortOneReconciliationProperties,
    ): PortOneReconciliationSource = PortOneReconciliationSource(portOneReconciliationRestClient, portOneProperties, properties)

    companion object {
        const val PORT_ONE_RECONCILIATION_REST_CLIENT = "portOneReconciliationRestClient"
        private const val CONNECT_TIMEOUT_MS = 2_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
