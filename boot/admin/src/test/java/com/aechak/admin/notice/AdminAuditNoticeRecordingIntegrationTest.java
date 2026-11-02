package com.aechak.admin.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.audit.auditlog.AdminAuditLog;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import com.aechak.webcommon.trace.TraceIdFilter;
import com.jayway.jsonpath.JsonPath;
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
 * [통합] 공지 수정·게시·내림이 감사 행을 남기는지 — 그리고 초안 생성은 남기지 <b>않는지</b>.
 *
 * <p>게시·내림은 공지가 밖에 드러나거나 사라지는 조치이고, 수정은 이미 읽은 사람과 다른 내용이
 * 되는 조치라 셋 다 기록한다. 초안 생성만 면제했고 그 판단도 테스트로 고정한다 — 면제가 "결정"이
 * 아니라 "빠뜨림"으로 바뀌는 것을 막는 자리다.
 */
class AdminAuditNoticeRecordingIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/notices";

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private FilterChainProxy adminSecurityChain;

    private MockMvc mockMvc;
    private long adminId;
    private String adminToken;

    @BeforeEach
    void setUpMockMvcAndActor() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(new TraceIdFilter(), adminSecurityChain)
                .build();
        adminId = createUser(UserStatus.ACTIVE, UserRole.ADMIN);
        adminToken = mintAccessToken(adminId);
    }

    /** 생성은 면제다. 공지가 실제로 만들어졌는데도 행이 없다는 것을 함께 단언한다. */
    @Test
    void 초안_생성은_행을_남기지_않고_공지만_만든다() throws Exception {
        String noticeId = createDraft("점검 안내", "10월 31일 02시부터 04시까지 점검합니다");

        assertThat(noticeId).isNotBlank();
        assertThat(auditRows()).isEmpty();
    }

    @Test
    void 공지_게시는_성공_행을_남긴다() throws Exception {
        String noticeId = createDraft("점검 안내", "10월 31일 02시부터 04시까지 점검합니다");

        publish(noticeId).andExpect(status().isOk());

        AdminAuditLog row = onlyRow();
        assertThat(row.getActorUserId()).isEqualTo(adminId);
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.NOTICE_PUBLISHED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.NOTICE);
        // 어드민 경로가 공지를 공개 식별자로 가리키므로 행에도 그 값이 들어간다
        assertThat(row.getTargetId()).isEqualTo(noticeId);
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(row.getHttpStatus()).isEqualTo(200);
        assertThat(row.getRequestId()).as("X-Trace-Id가 행에 들어오지 않았다").isNotBlank();
        // 게시는 사유를 받는 계약이 아니다. internalMemo는 공지 행이 들고 있어 사본을 두지 않는다
        assertThat(row.getReason()).isNull();
    }

    @Test
    void 공지_내림은_성공_행을_남긴다() throws Exception {
        String noticeId = createDraft("이벤트 종료", "쿠폰 이벤트가 종료되었습니다");
        publish(noticeId).andExpect(status().isOk());

        mockMvc.perform(bearer(post(BASE + "/" + noticeId + "/unpublish"), adminToken))
                .andExpect(status().isOk());

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(2);
        AdminAuditLog pulled = rows.get(1);
        assertThat(pulled.getAction()).isEqualTo(AdminAuditAction.NOTICE_UNPUBLISHED);
        assertThat(pulled.getTargetId()).isEqualTo(noticeId);
        assertThat(pulled.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
    }

    @Test
    void 공지_수정은_성공_행을_남긴다() throws Exception {
        String noticeId = createDraft("점검 안내", "10월 31일 02시부터 04시까지 점검합니다");

        update(noticeId, "점검 시간 변경", "점검이 03시부터 05시로 미뤄졌습니다").andExpect(status().isOk());

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.NOTICE_UPDATED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.NOTICE);
        assertThat(row.getTargetId()).isEqualTo(noticeId);
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
    }

    /** 거부당한 시도도 남는다 — 되돌릴 수 없지 않은 조치라도 누가 무엇을 하려 했는지가 기록의 요점이다 */
    @Test
    void 이미_게시된_공지의_재게시는_실패_행을_남긴다() throws Exception {
        String noticeId = createDraft("점검 안내", "10월 31일 02시부터 04시까지 점검합니다");
        publish(noticeId).andExpect(status().isOk());

        publish(noticeId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(150006));

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(2);
        AdminAuditLog failure = rows.get(1);
        assertThat(failure.getAction()).isEqualTo(AdminAuditAction.NOTICE_PUBLISHED);
        assertThat(failure.getTargetId()).isEqualTo(noticeId);
        assertThat(failure.getResult()).isEqualTo(AdminAuditResult.FAILURE);
        assertThat(failure.getHttpStatus()).isEqualTo(409);
    }

    /** 없는 공지를 집은 시도도 요청이 들고 온 식별자와 함께 남는다 */
    @Test
    void 없는_공지_게시_시도도_실패_행으로_남는다() throws Exception {
        publish("01JXXXXXXXXXXXXXXXXXXXXXXX")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(150000));

        AdminAuditLog row = onlyRow();
        assertThat(row.getTargetId()).isEqualTo("01JXXXXXXXXXXXXXXXXXXXXXXX");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.FAILURE);
    }

    @Test
    void 공지_조회는_행을_남기지_않는다() throws Exception {
        String noticeId = createDraft("점검 안내", "10월 31일 02시부터 04시까지 점검합니다");

        mockMvc.perform(bearer(get(BASE), adminToken)).andExpect(status().isOk());
        mockMvc.perform(bearer(get(BASE + "/" + noticeId), adminToken)).andExpect(status().isOk());

        assertThat(auditRows()).isEmpty();
    }

    private String createDraft(String title, String content) throws Exception {
        String response = mockMvc.perform(bearer(post(BASE), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(title, content)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.noticeId");
    }

    private ResultActions update(String noticeId, String title, String content) throws Exception {
        return mockMvc.perform(bearer(put(BASE + "/" + noticeId), adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveBody(title, content)));
    }

    private ResultActions publish(String noticeId) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + noticeId + "/publish"), adminToken));
    }

    private static String saveBody(String title, String content) {
        return """
                {"title": "%s", "content": "%s", "category": "MAINTENANCE", "audience": "ALL", \
                "pinned": false, "internalMemo": "담당자 확인 완료"}
                """
                .formatted(title, content);
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

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
