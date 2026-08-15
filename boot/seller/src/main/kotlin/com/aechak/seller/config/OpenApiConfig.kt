package com.aechak.seller.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** OpenAPI(Swagger UI) 문서 설정. Swagger UI = /swagger-ui.html, 스펙(JSON) = /v3/api-docs. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("aechak Seller API")
                .version("v1")
                .description("애착 셀러센터 API 문서"),
        )
}
