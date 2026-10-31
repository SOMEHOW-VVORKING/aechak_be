package com.aechak.infra.client.ai

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * 초안 호출용 RestClient 공급. PortOneConfig와 같은 모양이다.
 *
 * 어댑터가 RestClient를 내부에서 만들면 MockRestServiceServer.bindTo를 꽂을 수 없어 응답·실패
 * 번역을 테스트할 수 없다(PortOneAdapterTest 주석이 그 사유를 적었다) — 그래서 빈으로 공급한다.
 *
 * 설정 바인딩은 @ConfigurationPropertiesScan이 아니라 @EnableConfigurationProperties로 한다 —
 * SellerApiApplication이 @ComponentScan을 직접 선언해 프로퍼티 자동 스캔이 꺼져 있다
 * (S3Config·CoolSmsConfig·PortOneConfig가 모두 그 모양이다).
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
