package com.aechak.seller.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * API 경로 접두(api.base-path)를 핸들러 매핑 레벨에서 일괄 부착한다 — api 모듈과 같은 접두를 쓴다
 * (호스트로 분리되므로 경로 자체는 api 시절과 동일해야 FE 수정이 없다).
 */
@Configuration(proxyBeanMethods = false)
class WebConfig(
    @param:Value("\${api.base-path}") private val basePath: String,
) : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(basePath, HandlerTypePredicate.forBasePackage("com.aechak"))
    }
}
