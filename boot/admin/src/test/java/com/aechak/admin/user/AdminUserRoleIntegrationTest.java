package com.aechak.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.application.support.PageQuery;
import com.aechak.application.support.PageResult;
import com.aechak.application.user.user.usecase.AdminUserRoleUseCase;
import com.aechak.application.user.user.usecase.result.AdminUserRoleResult;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.user.error.UserErrorCode;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 관리자 역할 승격·강등·명부 조회 통합 테스트 — users.role이 실제로 바뀌는지 DB로 확인한다. */
class AdminUserRoleIntegrationTest extends IntegrationTestBase {

    private static final long ACTOR_ADMIN_ID = 777L;

    @Autowired
    private AdminUserRoleUseCase adminUserRoleUseCase;

    @Test
    void 활성_일반_회원_승격은_users_role을_ADMIN으로_바꾼다() {
        long targetUserId = createUser(UserStatus.ACTIVE);

        adminUserRoleUseCase.promote(ACTOR_ADMIN_ID, targetUserId);

        assertThat(roleOf(targetUserId)).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 이미_관리자인_계정_승격은_30012로_거부된다() {
        long targetUserId = createUser(UserStatus.ACTIVE, UserRole.ADMIN);

        assertThatThrownBy(() -> adminUserRoleUseCase.promote(ACTOR_ADMIN_ID, targetUserId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.USER_ALREADY_ADMIN);
    }

    @Test
    void 정지된_계정_승격은_30017로_거부되고_role이_그대로다() {
        long targetUserId = createUser(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> adminUserRoleUseCase.promote(ACTOR_ADMIN_ID, targetUserId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_PROMOTABLE);
        assertThat(roleOf(targetUserId)).isEqualTo(UserRole.GENERAL);
    }

    @Test
    void 온보딩_미완료_계정_승격도_30017로_거부된다() {
        long targetUserId = createUser(UserStatus.PENDING_ONBOARDING);

        assertThatThrownBy(() -> adminUserRoleUseCase.promote(ACTOR_ADMIN_ID, targetUserId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_PROMOTABLE);
        assertThat(roleOf(targetUserId)).isEqualTo(UserRole.GENERAL);
    }

    @Test
    void 정지된_관리자_재승격은_상태보다_30012가_먼저_판정된다() {
        long targetUserId = createUser(UserStatus.SUSPENDED, UserRole.ADMIN);

        assertThatThrownBy(() -> adminUserRoleUseCase.promote(ACTOR_ADMIN_ID, targetUserId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.USER_ALREADY_ADMIN);
    }

    @Test
    void 없는_계정_승격은_30000으로_거부된다() {
        assertThatThrownBy(() -> adminUserRoleUseCase.promote(ACTOR_ADMIN_ID, 999999L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 없는_계정_강등은_30000으로_거부된다() {
        assertThatThrownBy(() -> adminUserRoleUseCase.demote(ACTOR_ADMIN_ID, 999999L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 강등은_role을_GENERAL로_되돌리고_재호출에도_멱등하다() {
        long targetUserId = createUser(UserStatus.ACTIVE, UserRole.ADMIN);

        adminUserRoleUseCase.demote(ACTOR_ADMIN_ID, targetUserId);
        adminUserRoleUseCase.demote(ACTOR_ADMIN_ID, targetUserId);

        assertThat(roleOf(targetUserId)).isEqualTo(UserRole.GENERAL);
    }

    @Test
    void 명부_조회는_관리자만_가입순으로_주고_전체_개수를_센다() {
        long firstAdmin = createUser(UserStatus.ACTIVE, UserRole.ADMIN);
        long secondAdmin = createUser(UserStatus.ACTIVE, UserRole.ADMIN);
        long thirdAdmin = createUser(UserStatus.ACTIVE, UserRole.ADMIN);
        createUser(UserStatus.ACTIVE);

        PageResult<AdminUserRoleResult> firstPage = adminUserRoleUseCase.listAdmins(PageQuery.of(0, 2));
        PageResult<AdminUserRoleResult> secondPage = adminUserRoleUseCase.listAdmins(PageQuery.of(1, 2));

        assertThat(firstPage.getTotalCount()).isEqualTo(3);
        assertThat(firstPage.getItems())
                .extracting(AdminUserRoleResult::userId)
                .containsExactly(firstAdmin, secondAdmin);
        assertThat(secondPage.getItems())
                .extracting(AdminUserRoleResult::userId)
                .containsExactly(thirdAdmin);
        assertThat(secondPage.getItems())
                .allSatisfy(item -> assertThat(item.role()).isEqualTo(UserRole.ADMIN));
    }

    @Test
    void 관리자가_없으면_명부는_빈_목록과_0을_준다() {
        createUser(UserStatus.ACTIVE);

        PageResult<AdminUserRoleResult> page = adminUserRoleUseCase.listAdmins(PageQuery.of(0, 20));

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotalCount()).isZero();
    }

    @Test
    void 범위를_넘긴_페이지는_빈_목록을_주고_전체_개수는_유지한다() {
        createUser(UserStatus.ACTIVE, UserRole.ADMIN);
        createUser(UserStatus.ACTIVE, UserRole.ADMIN);

        PageResult<AdminUserRoleResult> page = adminUserRoleUseCase.listAdmins(PageQuery.of(5, 2));

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotalCount()).isEqualTo(2);
    }

    private UserRole roleOf(long userId) {
        return tx.execute(txStatus -> em.createQuery("select u.role from User u where u.id = :id", UserRole.class)
                .setParameter("id", userId)
                .getSingleResult());
    }
}
