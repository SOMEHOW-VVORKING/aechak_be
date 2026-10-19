package com.aechak.admin.config;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기동 시 관리자로 승격할 명부 설정.
 * dev는 SSM(/aechak/dev/admin/)의 ADMIN_BOOTSTRAP_* 값이 프로파일 yml을 통해 바인딩된다.
 */
@ConfigurationProperties("admin.bootstrap")
public record AdminBootstrapProperties(boolean enabled, List<Long> userIds) {

    public AdminBootstrapProperties {
        userIds = userIds == null
                ? List.of()
                : userIds.stream().filter(Objects::nonNull).toList();
    }
}
