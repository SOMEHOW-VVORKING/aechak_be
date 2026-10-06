package com.aechak.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API 경로 접두(api.base-path)를 핸들러 매핑 레벨에서 일괄 부착한다 —
 * 계약 경로는 api와 동일한 "/api/v1/admin/…"을 유지한다(모듈 분리는 배포 토폴로지 관심사).
 * base package 한정인 이유: springdoc 등 외부 라이브러리 컨트롤러에 접두가 번지는 것 방지.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final String basePath;

    public WebConfig(@Value("${api.base-path}") String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(basePath, HandlerTypePredicate.forBasePackage("com.aechak"));
    }
}
