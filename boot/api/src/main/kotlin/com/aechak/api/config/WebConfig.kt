package com.aechak.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * API 경로 접두(api.base-path)를 핸들러 매핑 레벨에서 일괄 부착한다 —
 * 컨트롤러는 접두 없이 도메인 경로만 선언한다(예: "/auth", "/users").
 * base package 한정인 이유: springdoc 등 외부 라이브러리 컨트롤러에 접두가 번지는 것 방지.
 * Security 매처·상태검증 화이트리스트도 같은 프로퍼티를 쓴다 — 값 변경 시 세 곳이 함께 움직인다.
 */
@Configuration(proxyBeanMethods = false)
class WebConfig(
    @param:Value("\${api.base-path}") private val basePath: String,
) : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(basePath, HandlerTypePredicate.forBasePackage("com.aechak"))
    }
}
