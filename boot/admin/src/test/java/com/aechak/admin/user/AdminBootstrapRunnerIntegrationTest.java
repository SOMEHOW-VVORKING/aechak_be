package com.aechak.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.aechak.admin.config.AdminBootstrapProperties;
import com.aechak.admin.config.AdminBootstrapRunner;
import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.application.user.user.usecase.AdminUserRoleUseCase;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * 기동 부트스트랩 통합 테스트 — 러너를 직접 호출한다(@SpringBootTest는 ApplicationRunner를 돌리지 않는다).
 * 명부는 테스트별로 달라야 해서 컨텍스트를 새로 띄우지 않고 설정 객체를 손으로 만든다.
 */
class AdminBootstrapRunnerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AdminUserRoleUseCase adminUserRoleUseCase;

    @Autowired
    private AdminBootstrapProperties bootstrapProperties;

    @Autowired
    private AdminBootstrapRunner assembledRunner;

    @Test
    void 설정_명부의_활성_계정을_관리자로_승격한다() {
        long firstUserId = createUser(UserStatus.ACTIVE);
        long secondUserId = createUser(UserStatus.ACTIVE);

        runBootstrap(true, firstUserId, secondUserId);

        assertThat(roleOf(firstUserId)).isEqualTo(UserRole.ADMIN);
        assertThat(roleOf(secondUserId)).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 재기동해도_이미_관리자인_대상을_그대로_둔다() {
        long userId = createUser(UserStatus.ACTIVE);

        runBootstrap(true, userId);
        assertThatCode(() -> runBootstrap(true, userId)).doesNotThrowAnyException();

        assertThat(roleOf(userId)).isEqualTo(UserRole.ADMIN);
        assertThat(adminCount()).isEqualTo(1);
    }

    @Test
    void 명부에_없는_userId가_섞여도_나머지를_승격하고_부팅을_계속한다() {
        long existingUserId = createUser(UserStatus.ACTIVE);

        assertThatCode(() -> runBootstrap(true, 999999L, existingUserId)).doesNotThrowAnyException();

        assertThat(roleOf(existingUserId)).isEqualTo(UserRole.ADMIN);
        assertThat(adminCount()).isEqualTo(1);
    }

    @Test
    void 정지된_계정이_명부에_있어도_부팅을_계속한다() {
        long suspendedUserId = createUser(UserStatus.SUSPENDED);
        long activeUserId = createUser(UserStatus.ACTIVE);

        assertThatCode(() -> runBootstrap(true, suspendedUserId, activeUserId)).doesNotThrowAnyException();

        assertThat(roleOf(suspendedUserId)).isEqualTo(UserRole.GENERAL);
        assertThat(roleOf(activeUserId)).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 비활성화되어_있으면_명부가_있어도_승격하지_않는다() {
        long userId = createUser(UserStatus.ACTIVE);

        runBootstrap(false, userId);

        assertThat(roleOf(userId)).isEqualTo(UserRole.GENERAL);
    }

    @Test
    void 명부가_비면_러너는_아무것도_하지_않는다() {
        long userId = createUser(UserStatus.ACTIVE);

        runBootstrap(true);

        assertThat(roleOf(userId)).isEqualTo(UserRole.GENERAL);
    }

    @Test
    void 조립된_러너는_빈_명부_설정을_바인딩해_그대로_넘어간다() {
        long userId = createUser(UserStatus.ACTIVE);

        assertThat(bootstrapProperties.enabled()).isFalse();
        assertThat(bootstrapProperties.userIds()).isEmpty();
        assertThatCode(() -> assembledRunner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
        assertThat(roleOf(userId)).isEqualTo(UserRole.GENERAL);
    }

    private void runBootstrap(boolean enabled, Long... userIds) {
        new AdminBootstrapRunner(new AdminBootstrapProperties(enabled, List.of(userIds)), adminUserRoleUseCase)
                .run(new DefaultApplicationArguments());
    }

    private UserRole roleOf(long userId) {
        return tx.execute(txStatus -> em.createQuery("select u.role from User u where u.id = :id", UserRole.class)
                .setParameter("id", userId)
                .getSingleResult());
    }

    private long adminCount() {
        return tx.execute(txStatus -> em.createQuery("select count(u) from User u where u.role = :role", Long.class)
                .setParameter("role", UserRole.ADMIN)
                .getSingleResult());
    }
}
