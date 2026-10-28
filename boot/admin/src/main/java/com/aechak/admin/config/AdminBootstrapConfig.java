package com.aechak.admin.config;

import com.aechak.application.user.user.usecase.AdminUserRoleUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 관리자 기동 부트스트랩 조립 — 설정 바인딩과 러너 등록. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminBootstrapConfig {

    @Bean
    public AdminBootstrapRunner adminBootstrapRunner(
            AdminBootstrapProperties properties, AdminUserRoleUseCase adminUserRoleUseCase) {
        return new AdminBootstrapRunner(properties, adminUserRoleUseCase);
    }
}
