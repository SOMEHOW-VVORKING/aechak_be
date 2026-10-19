package com.aechak.admin.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * 계약 — 설정의 쉼표 구분 userId 문자열이 목록으로 들어와야 한다.
 * dev는 SSM 값을 프로파일 yml의 placeholder로 받으므로 미등재 시 빈 문자열이 흘러온다.
 */
class AdminBootstrapPropertiesTest {

    @Test
    void 쉼표로_구분된_userId_문자열이_목록으로_바인딩된다() {
        AdminBootstrapProperties properties =
                bind(Map.of("admin.bootstrap.enabled", "true", "admin.bootstrap.user-ids", "7,42"));

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.userIds()).containsExactly(7L, 42L);
    }

    @Test
    void 빈_문자열_명부는_빈_목록이_된다() {
        AdminBootstrapProperties properties =
                bind(Map.of("admin.bootstrap.enabled", "true", "admin.bootstrap.user-ids", ""));

        assertThat(properties.userIds()).isEmpty();
    }

    @Test
    void 명부_키가_없으면_빈_목록이_된다() {
        AdminBootstrapProperties properties = bind(Map.of("admin.bootstrap.enabled", "true"));

        assertThat(properties.userIds()).isEmpty();
    }

    @Test
    void 설정이_아예_없으면_비활성이고_명부가_빈다() {
        AdminBootstrapProperties properties = bind(Map.of());

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.userIds()).isEmpty();
    }

    private AdminBootstrapProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bindOrCreate("admin.bootstrap", AdminBootstrapProperties.class);
    }
}
