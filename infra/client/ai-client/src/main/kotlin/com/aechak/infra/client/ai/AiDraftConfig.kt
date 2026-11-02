package com.aechak.infra.client.ai

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * 초안 호출용 RestClient 공급. PortOneConfig 와 같은 모양이다 — 어댑터가 직접 만들면
 * MockRestServiceServer.bindTo 를 꽂을 수 없어 응답·실패 번역을 테스트할 수 없다.
 *
 * 설정 바인딩은 @EnableConfigurationProperties 로 한다. SellerApiApplication 이 @ComponentScan 을
 * 직접 선언해 프로퍼티 자동 스캔이 꺼져 있다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiDraftProperties::class)
class AiDraftConfig {
    @Bean(name = [AI_DRAFT_REST_CLIENT])
    fun aiDraftRestClient(properties: AiDraftProperties): RestClient =
        RestClient
            .builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(properties.connectTimeout)
                    setReadTimeout(properties.readTimeout)
                },
            ).build()

    companion object {
        const val AI_DRAFT_REST_CLIENT = "aiDraftRestClient"
    }
}
