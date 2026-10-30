package com.aechak.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.audit.auditlog.AdminAuditLog;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.domain.user.sanction.UserSanction;
import com.aechak.domain.user.sanction.enums.SanctionAction;
import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import com.aechak.webcommon.trace.TraceIdFilter;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * [통합] 회원 제재·해제가 감사 행을 남기는지 — 그리고 <b>저장된 내용</b>이 맞는지.
 *
 * <p>"기록이 호출됐다"로는 부족하다. 이 조치에서 조용히 틀릴 자리가 둘 있다.
 *
 * <ul>
 *   <li>대상 이름을 조치 뒤에 집는다 — 탈퇴 처리는 프로필을 지우므로 가장 무거운 제재의 행만 이름이 빈다.
 *   <li>사유를 조치 뒤에 올린다 — 거부당한 시도의 사유 칸이 비고, 사유 없는 제재 기록은 분쟁에서 못 쓴다.
 * </ul>
 *
 * 그래서 단언은 매번 행의 칸 값과 회원의 실제 상태·이력을 함께 본다.
 */
class AdminAuditMemberRecordingIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/members";

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private FilterChainProxy adminSecurityChain;

    private MockMvc mockMvc;
    private long actorId;
    private String actorToken;

    /** 운영 체인과 같은 모양으로 만든다(추적 필터가 시큐리티보다 앞) — 그래야 행의 추적 id가 채워진다 */
    @BeforeEach
    void setUpMockMvcAndActor() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(new TraceIdFilter(), adminSecurityChain)
                .build();
        actorId = createMember("운영자", UserRole.ADMIN);
        actorToken = mintAccessToken(actorId);
    }

    @Test
    void 이용_제한은_성공_행에_대상_이름과_고정_어휘_사유를_남긴다() throws Exception {
        long targetId = createMember("제한대상", UserRole.GENERAL);

        sanction(targetId, "RESTRICT", "ABUSIVE_REVIEW", null, "3회 경고 후 재발").andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getActorUserId()).isEqualTo(actorId);
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.MEMBER_SANCTIONED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.MEMBER);
        assertThat(row.getTargetId()).isEqualTo(String.valueOf(targetId));
        assertThat(row.getTargetLabel()).isEqualTo("제한대상");
        assertThat(row.getReason()).isEqualTo("리뷰 욕설·비방 반복 — 3회 경고 후 재발");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(row.getHttpStatus()).isEqualTo(204);
        assertThat(row.getRequestId()).as("X-Trace-Id가 행에 들어오지 않았다").isNotBlank();
        assertThat(statusOf(targetId)).isEqualTo(UserStatus.RESTRICTED);
        assertThat(sanctionsOf(targetId)).hasSize(1);
    }

    /** 상세 사유가 없으면 어휘만 남는다 — 사유 칸이 비면 "왜 제재했나"를 행만 보고 알 수 없다 */
    @Test
    void 상세_사유가_없는_제재도_어휘를_사유로_남긴다() throws Exception {
        long targetId = createMember("정지대상", UserRole.GENERAL);

        sanction(targetId, "SUSPEND", "CHAT_DISPUTE", 7, null).andExpect(status().isNoContent());

        assertThat(onlyRow().getReason()).isEqualTo("채팅 분쟁 반복");
    }

    /**
     * 탈퇴 처리는 프로필을 지워 닉네임을 없앤다. 대상 이름을 조치 뒤에 집으면 <b>가장 무거운 제재의
     * 행만</b> 이름이 비는데, 그 행이야말로 나중에 "누구였나"를 물을 행이다.
     */
    @Test
    void 탈퇴_처리는_지워지기_전의_대상_이름을_행에_남긴다() throws Exception {
        long targetId = createMember("탈퇴대상", UserRole.GENERAL);

        sanction(targetId, "FORCE_WITHDRAW", "FALSE_REPORT", null, null).andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.MEMBER_SANCTIONED);
        assertThat(row.getTargetLabel()).isEqualTo("탈퇴대상");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(statusOf(targetId)).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(nicknameOf(targetId))
                .as("탈퇴 처리가 닉네임을 지우지 않았다면 이 테스트는 아무것도 지키지 못한다")
                .isNull();
    }

    @Test
    void 제재_해제는_해제_조치로_남고_상세_사유를_싣는다() throws Exception {
        long targetId = createMember("해제대상", UserRole.GENERAL);
        sanction(targetId, "RESTRICT", "ABUSIVE_REVIEW", null, null).andExpect(status().isNoContent());

        release(targetId, "소명 접수, 조치 철회").andExpect(status().isNoContent());

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(2);
        AdminAuditLog released = rows.get(1);
        assertThat(released.getAction()).isEqualTo(AdminAuditAction.MEMBER_SANCTION_RELEASED);
        assertThat(released.getTargetType()).isEqualTo(AdminAuditTargetType.MEMBER);
        assertThat(released.getTargetId()).isEqualTo(String.valueOf(targetId));
        assertThat(released.getTargetLabel()).isEqualTo("해제대상");
        assertThat(released.getReason()).isEqualTo("소명 접수, 조치 철회");
        assertThat(released.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(statusOf(targetId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionsOf(targetId).get(0).getAction()).isEqualTo(SanctionAction.RELEASE);
    }

    /** 사유를 생략한 해제는 사유 칸을 비운다 — 공백을 적어 "사유가 있었다"로 보이게 하지 않는다 */
    @Test
    void 사유_없는_해제는_사유_칸을_비운다() throws Exception {
        long targetId = createMember("해제대상", UserRole.GENERAL);
        sanction(targetId, "RESTRICT", "ABUSIVE_REVIEW", null, null).andExpect(status().isNoContent());

        release(targetId, "   ").andExpect(status().isNoContent());

        assertThat(auditRows().get(1).getReason()).isNull();
    }

    /**
     * 거부당한 시도도 대상과 사유가 채워진 채 남아야 한다 — 관리자 계정 제재는 "하려 했지만 막혔다"를
     * 남기는 것이 요점인 조치다. 동시에 그 계정이 전혀 건드려지지 않았음을 상태·역할로 확인한다.
     */
    @Test
    void 관리자_제재_시도는_대상이_채워진_실패_행을_남기고_그_계정을_건드리지_않는다() throws Exception {
        long adminTargetId = createMember("다른운영자", UserRole.ADMIN);

        sanction(adminTargetId, "BAN", "FALSE_REPORT", null, "권한 남용 의심")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30021));

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.MEMBER_SANCTIONED);
        assertThat(row.getTargetId()).isEqualTo(String.valueOf(adminTargetId));
        assertThat(row.getTargetLabel()).isEqualTo("다른운영자");
        assertThat(row.getReason()).isEqualTo("허위 신고 반복 — 권한 남용 의심");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.FAILURE);
        assertThat(row.getHttpStatus()).isEqualTo(409);
        assertThat(statusOf(adminTargetId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionsOf(adminTargetId)).isEmpty();
    }

    /** 없는 회원을 집은 시도도 남는다 — 대상을 읽기 전에 끊기는 경로라 이름은 비고 식별자만 남는다 */
    @Test
    void 없는_회원_제재_시도도_실패_행으로_남는다() throws Exception {
        sanction(999_999L, "RESTRICT", "ABUSIVE_REVIEW", null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(30000));

        AdminAuditLog row = onlyRow();
        assertThat(row.getTargetId()).isEqualTo("999999");
        assertThat(row.getTargetLabel()).isNull();
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.FAILURE);
    }

    /** 조회까지 남기면 조치가 조회 소음에 묻힌다 */
    @Test
    void 회원_조회는_행을_남기지_않는다() throws Exception {
        long targetId = createMember("조회대상", UserRole.GENERAL);

        mockMvc.perform(bearer(get(BASE), actorToken)).andExpect(status().isOk());
        mockMvc.perform(bearer(get(BASE + "/" + targetId), actorToken)).andExpect(status().isOk());

        assertThat(auditRows()).isEmpty();
    }

    private ResultActions sanction(
            long userId, String action, String reasonCode, Integer durationDays, String reasonDetail) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + userId + "/sanctions"), actorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanctionBody(action, reasonCode, durationDays, reasonDetail)));
    }

    private ResultActions release(long userId, String reasonDetail) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + userId + "/sanctions/release"), actorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonDetail\": " + json(reasonDetail) + ", \"memo\": null}"));
    }

    private static String sanctionBody(String action, String reasonCode, Integer durationDays, String reasonDetail) {
        return "{\"action\": \"" + action + "\", \"reasonCode\": \"" + reasonCode + "\", \"reasonDetail\": "
                + json(reasonDetail) + ", \"durationDays\": " + durationDays + ", \"memo\": null}";
    }

    private static String json(String text) {
        return text == null ? "null" : "\"" + text + "\"";
    }

    private AdminAuditLog onlyRow() {
        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).as("감사 행이 정확히 한 건이어야 한다").hasSize(1);
        return rows.get(0);
    }

    private List<AdminAuditLog> auditRows() {
        return Objects.requireNonNull(tx.execute(
                txStatus -> em.createQuery("select l from AdminAuditLog l order by l.id", AdminAuditLog.class)
                        .getResultList()));
    }

    private long createMember(String nickname, UserRole role) {
        long userId = Objects.requireNonNull(tx.execute(txStatus -> {
            User user = User.Companion.preRegister();
            em.persist(user);
            user.completeOnboarding(nickname);
            em.flush();
            return user.getId();
        }));
        tx.executeWithoutResult(txStatus -> em.createQuery("update User u set u.role = :role where u.id = :id")
                .setParameter("role", role)
                .setParameter("id", userId)
                .executeUpdate());
        return userId;
    }

    private UserStatus statusOf(long userId) {
        return tx.execute(txStatus -> em.createQuery("select u.status from User u where u.id = :id", UserStatus.class)
                .setParameter("id", userId)
                .getSingleResult());
    }

    private String nicknameOf(long userId) {
        return tx.execute(
                txStatus -> em.createQuery("select p.nickname from UserProfile p where p.userId = :id", String.class)
                        .setParameter("id", userId)
                        .getResultStream()
                        .findFirst()
                        .orElse(null));
    }

    /** 최신순 — 첫 원소가 가장 최근 조치다. */
    private List<UserSanction> sanctionsOf(long userId) {
        return tx.execute(txStatus -> em.createQuery(
                        "select s from UserSanction s where s.targetUser.id = :id order by s.id desc",
                        UserSanction.class)
                .setParameter("id", userId)
                .getResultList());
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
