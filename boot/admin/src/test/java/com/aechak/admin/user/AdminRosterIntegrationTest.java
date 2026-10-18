package com.aechak.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 관리자 명부 API 통합 테스트 — 조회·승격·강등과 자기 역할 변경·마지막 관리자 가드를 HTTP로 검증한다. */
class AdminRosterIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/admins";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;
    private long actorId;
    private String actorToken;

    @BeforeEach
    void setUpMockMvcAndActor() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        actorId = createOnboardedUser("운영자", UserRole.ADMIN);
        actorToken = mintAccessToken(actorId);
    }

    @Test
    void 토큰_없이_목록을_조회하면_401_20004_를_반환한다() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(20004));
    }

    @Test
    void 일반_GENERAL_토큰은_목록_접근이_403_20011_로_막힌다() throws Exception {
        mockMvc.perform(bearer(get(BASE), mintAccessToken(createUser(), UserRole.GENERAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }

    @Test
    void 목록은_관리자만_가입순으로_주고_요청자_행에_isSelf를_표시한다() throws Exception {
        long otherAdminId = createOnboardedUser("부운영자", UserRole.ADMIN);
        createOnboardedUser("일반회원", UserRole.GENERAL);

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].userId").value(actorId))
                .andExpect(jsonPath("$.data.items[0].nickname").value("운영자"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].joinedAt").exists())
                .andExpect(jsonPath("$.data.items[0].isSelf").value(true))
                .andExpect(jsonPath("$.data.items[1].userId").value(otherAdminId))
                .andExpect(jsonPath("$.data.items[1].isSelf").value(false));
    }

    @Test
    void 온보딩_전_관리자는_nickname이_null로_나온다() throws Exception {
        createUser(UserStatus.PENDING_ONBOARDING, UserRole.ADMIN);

        mockMvc.perform(bearer(get(BASE).param("size", "20"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[1].nickname").doesNotExist());
    }

    @Test
    void 형식이_틀린_페이지_파라미터는_보정되어_200을_준다() throws Exception {
        mockMvc.perform(bearer(get(BASE).param("page", "-1").param("size", "999"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void 승격은_204이고_명부에_포함된다() throws Exception {
        long targetId = createOnboardedUser("새관리자", UserRole.GENERAL);

        mockMvc.perform(bearer(post(BASE), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promoteBody(targetId)))
                .andExpect(status().isNoContent());

        assertThat(roleOf(targetId)).isEqualTo(UserRole.ADMIN);
        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2));
    }

    @Test
    void 이미_관리자인_계정_승격은_409_30012_를_반환한다() throws Exception {
        long targetId = createOnboardedUser("기존관리자", UserRole.ADMIN);

        mockMvc.perform(bearer(post(BASE), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promoteBody(targetId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30012));
    }

    @Test
    void 자기_자신_승격은_403_30014_를_반환한다() throws Exception {
        // 강등된 직후 옛 ADMIN 토큰으로 자기 자격을 되돌리는 경로를 막는다
        long staleActorId = createOnboardedUser("강등된운영자", UserRole.GENERAL);

        mockMvc.perform(bearer(post(BASE), mintAccessToken(staleActorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promoteBody(staleActorId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(30014));
        assertThat(roleOf(staleActorId)).isEqualTo(UserRole.GENERAL);
    }

    @Test
    void 없는_계정_승격은_404_30000_를_반환한다() throws Exception {
        mockMvc.perform(bearer(post(BASE), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promoteBody(999999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(30000));
    }

    @Test
    void userId가_빠진_승격_요청은_400_90001_를_반환한다() throws Exception {
        mockMvc.perform(bearer(post(BASE), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));
    }

    @Test
    void 관리자_둘_중_하나의_강등은_204이고_명부에서_빠진다() throws Exception {
        long targetId = createOnboardedUser("퇴임관리자", UserRole.ADMIN);

        mockMvc.perform(bearer(delete(BASE + "/" + targetId), actorToken)).andExpect(status().isNoContent());

        assertThat(roleOf(targetId)).isEqualTo(UserRole.GENERAL);
        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(actorId));
    }

    @Test
    void 마지막_관리자_강등은_409_30013_를_반환한다() throws Exception {
        long lastAdminId = createOnboardedUser("마지막관리자", UserRole.ADMIN);
        // 명부를 1명으로 만들려고 요청자의 저장값만 내린다 — 옛 ADMIN 토큰은 그대로 게이트를 통과한다
        setRole(actorId, UserRole.GENERAL);

        mockMvc.perform(bearer(delete(BASE + "/" + lastAdminId), actorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30013));
        assertThat(roleOf(lastAdminId)).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 자기_자신_강등은_403_30014_를_반환한다() throws Exception {
        mockMvc.perform(bearer(delete(BASE + "/" + actorId), actorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(30014));
        assertThat(roleOf(actorId)).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 일반_회원_강등은_204로_멱등하다() throws Exception {
        long generalId = createOnboardedUser("일반회원", UserRole.GENERAL);

        mockMvc.perform(bearer(delete(BASE + "/" + generalId), actorToken)).andExpect(status().isNoContent());

        assertThat(roleOf(generalId)).isEqualTo(UserRole.GENERAL);
    }

    private String promoteBody(long userId) {
        return "{\"userId\": " + userId + "}";
    }

    /** 온보딩까지 끝낸(프로필·닉네임을 가진) 계정을 심고 역할을 지정한다. */
    private long createOnboardedUser(String nickname, UserRole role) {
        long userId = Objects.requireNonNull(tx.execute(txStatus -> {
            User user = User.Companion.preRegister();
            em.persist(user);
            user.completeOnboarding(nickname);
            em.flush();
            return user.getId();
        }));
        setRole(userId, role);
        return userId;
    }

    private void setRole(long userId, UserRole role) {
        tx.executeWithoutResult(txStatus -> em.createQuery("update User u set u.role = :role where u.id = :id")
                .setParameter("role", role)
                .setParameter("id", userId)
                .executeUpdate());
    }

    private UserRole roleOf(long userId) {
        return tx.execute(txStatus -> em.createQuery("select u.role from User u where u.id = :id", UserRole.class)
                .setParameter("id", userId)
                .getSingleResult());
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
