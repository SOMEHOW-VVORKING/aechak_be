package com.aechak.admin.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 어드민 게이트 통합 테스트 — 모듈 경계 인가(전 요청 role=ADMIN)와 상태 게이트를 검증한다.
 * 표적은 테스트 전용 프로브(/admin/security-probe) — 컨트롤러가 늘어도 게이트 검증은 이 고정 표적을 유지한다.
 */
class AdminSecurityIntegrationTest extends IntegrationTestBase {

    private static final String PROBE = "/api/v1/admin/security-probe";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
    }

    @Test
    void 토큰_없이_호출하면_401_20004_을_반환한다() throws Exception {
        mockMvc
                .perform(get(PROBE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(20004));
    }

    @Test
    void 일반_GENERAL_토큰은_403_20011_으로_거부한다() throws Exception {
        long userId = createUser(UserStatus.ACTIVE);
        mockMvc
                .perform(bearer(get(PROBE), mintAccessToken(userId, UserRole.GENERAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }

    @Test
    void ADMIN_토큰은_게이트를_통과한다() throws Exception {
        long adminId = createUser(UserStatus.ACTIVE);
        mockMvc
                .perform(bearer(get(PROBE), mintAccessToken(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void 정지된_계정은_ADMIN_토큰이라도_403_20005_으로_거부한다() throws Exception {
        long suspendedId = createUser(UserStatus.SUSPENDED);
        mockMvc
                .perform(bearer(get(PROBE), mintAccessToken(suspendedId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20005));
    }

    @Test
    void 온보딩_미완_계정은_403_20006_으로_거부한다_어드민엔_온보딩_허용_경로가_없다() throws Exception {
        long pendingId = createUser(UserStatus.PENDING_ONBOARDING);
        mockMvc
                .perform(bearer(get(PROBE), mintAccessToken(pendingId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20006));
    }

    @Test
    void 헬스체크는_토큰_없이_접근할_수_있다() throws Exception {
        mockMvc
                .perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
