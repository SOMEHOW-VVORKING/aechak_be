package com.aechak.infra.client.payment

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false) // 프록시로 사용할 곳이 없어서 off
@EnableConfigurationProperties(PortOneProperties::class)
class PortOneConfig {
    @Bean(name = [PORT_ONE_REST_CLIENT])
    fun portOneRestClient(properties: PortOneProperties): RestClient =
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

    companion object {
        const val PORT_ONE_REST_CLIENT = "portOneRestClient"

        private const val CONNECT_TIMEOUT_MS = 2_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
