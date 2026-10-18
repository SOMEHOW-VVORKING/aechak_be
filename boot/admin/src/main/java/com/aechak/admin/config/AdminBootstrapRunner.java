package com.aechak.admin.config;

import com.aechak.application.user.user.usecase.AdminUserRoleUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 기동마다 설정 명부를 관리자로 멱등 승격한다 — 설정이 비어 있으면 아무것도 하지 않는다.
 * 대상별 성공·실패 처리는 유스케이스 소관이라 여기서는 예외를 잡지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final AdminUserRoleUseCase adminUserRoleUseCase;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || properties.userIds().isEmpty()) {
            log.info(
                    "관리자 기동 부트스트랩을 건너뜁니다 enabled={} userIdCount={}",
                    properties.enabled(),
                    properties.userIds().size());
            return;
        }
        adminUserRoleUseCase.ensureBootstrapAdmins(properties.userIds());
    }
}
