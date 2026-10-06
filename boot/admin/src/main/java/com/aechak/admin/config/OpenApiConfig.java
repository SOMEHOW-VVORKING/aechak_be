package com.aechak.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI(Swagger UI) 문서 설정.
 *
 * <p>Swagger UI = /swagger-ui.html, 스펙(JSON) = /v3/api-docs.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("aechak ADMIN API")
                        .version("v1")
                        .description("애착 어드민 API 문서 — 운영자(role=ADMIN) 전용"));
    }
}
