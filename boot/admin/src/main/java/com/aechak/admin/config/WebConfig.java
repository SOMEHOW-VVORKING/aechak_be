package com.aechak.admin.config;

import com.aechak.admin.audit.AdminAuditInterceptor;
import com.aechak.application.audit.usecase.AdminAuditLogUseCase;
import com.aechak.webcommon.trace.TraceIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
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
    private final AdminAuditLogUseCase adminAuditLogUseCase;

    public WebConfig(@Value("${api.base-path}") String basePath, AdminAuditLogUseCase adminAuditLogUseCase) {
        this.basePath = basePath;
        this.adminAuditLogUseCase = adminAuditLogUseCase;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(basePath, HandlerTypePredicate.forBasePackage("com.aechak"));
    }

    /** 감사 기록은 핸들러가 끝난 뒤 쓴다 — 조치의 트랜잭션이 커밋된 자리이고 거부도 거기서 보인다. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuditInterceptor(adminAuditLogUseCase));
    }

    /**
     * X-Trace-Id 필터 — 어드민에 등록이 빠져 있었다. 이 모듈의 로그 패턴은 이미
     * %X{traceId}를 쓰고 있어 지금까지 그 칸이 늘 비었고, 감사 행의 추적 id도 같은 값이라
     * 없으면 행과 서버 로그를 맞춰 볼 수단이 사라진다.
     *
     * <p>Security 필터 체인(기본 -100)보다 앞에 둬 인증 실패 로그에도 같은 id가 찍히게 한다.
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
