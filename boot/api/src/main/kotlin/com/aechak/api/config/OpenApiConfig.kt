package com.aechak.api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI(Swagger UI) 문서 설정.
 *
 * Swagger UI = /swagger-ui.html, 스펙(JSON) = /v3/api-docs.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("aechak API")
                .version("v1")
                .description("애착 API 문서"),
        )
}
