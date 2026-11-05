package com.aechak.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.audit.auditlog.AdminAuditLog;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.domain.inquiry.inquiry.Inquiry;
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;
import com.aechak.domain.inquiry.inquiry.enums.InquiryType;
import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
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
 * [통합] 서비스 문의 답변이 감사 행을 남기는지 — 그리고 처리 흐름 전이는 남기지 <b>않는지</b>.
 *
 * <p>답변은 되돌릴 수 없는 조치(1회 등록 + 완료 전이)라 기록한다. 처리중·보류 전이는 면제했고,
 * 그 판단도 테스트로 고정한다 — 면제가 "결정"이 아니라 "빠뜨림"으로 바뀌는 것을 막는 자리다.
 */
class AdminAuditInquiryRecordingIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/inquiries";

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private FilterChainProxy adminSecurityChain;

    private MockMvc mockMvc;
    private long adminId;
    private String adminToken;
    private long buyerId;

    @BeforeEach
    void setUpMockMvcAndActors() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(new TraceIdFilter(), adminSecurityChain)
                .build();
        adminId = createOnboardedUser("운영자", UserRole.ADMIN);
        adminToken = mintAccessToken(adminId);
        buyerId = createOnboardedUser("코코맘", UserRole.GENERAL);
    }

    @Test
    void 문의_답변은_성공_행을_남기고_문의를_완료로_옮긴다() throws Exception {
        long inquiryId = seedInquiry(InquiryType.ORDER_PAYMENT, "결제가 두 번 됐어요");

        answer(inquiryId, "중복 결제분은 영업일 3일 내 환불됩니다").andExpect(status().isOk());

        AdminAuditLog row = onlyRow();
        assertThat(row.getActorUserId()).isEqualTo(adminId);
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.INQUIRY_ANSWERED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.INQUIRY);
        assertThat(row.getTargetId()).isEqualTo(String.valueOf(inquiryId));
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(row.getHttpStatus()).isEqualTo(200);
        assertThat(row.getRequestId()).as("X-Trace-Id가 행에 들어오지 않았다").isNotBlank();
        // 문의에는 이름 칸이 없고 본문은 작성자가 쓴 글이라 지울 수 없는 기록으로 옮기지 않는다
        assertThat(row.getTargetLabel()).isNull();
        assertThat(row.getReason()).as("답변은 사유를 받는 조치가 아니다").isNull();

        Inquiry answered = reload(inquiryId);
        assertThat(answered.getStatus()).isEqualTo(InquiryStatus.DONE);
        assertThat(answered.getAnsweredByAdminId()).isEqualTo(adminId);
    }

    /** 거부당한 시도도 남는다 — 동시에 앞선 답변이 덮이지 않았음을 애그리거트로 확인한다 */
    @Test
    void 이미_답변한_문의의_재답변은_실패_행을_남기고_앞선_답변을_덮지_않는다() throws Exception {
        long inquiryId = seedInquiry(InquiryType.ACCOUNT, "로그인이 안 돼요");
        answer(inquiryId, "앱 최신 버전으로 업데이트해 주세요").andExpect(status().isOk());

        answer(inquiryId, "두 번째 답변")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(120008));

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(2);
        AdminAuditLog failure = rows.get(1);
        assertThat(failure.getAction()).isEqualTo(AdminAuditAction.INQUIRY_ANSWERED);
        assertThat(failure.getTargetId()).isEqualTo(String.valueOf(inquiryId));
        assertThat(failure.getResult()).isEqualTo(AdminAuditResult.FAILURE);
        assertThat(failure.getHttpStatus()).isEqualTo(409);
        assertThat(reload(inquiryId).getAnswer()).isEqualTo("앱 최신 버전으로 업데이트해 주세요");
    }

    /** 없는 문의를 집은 시도도 요청이 들고 온 식별자와 함께 남는다 */
    @Test
    void 없는_문의_답변_시도도_실패_행으로_남는다() throws Exception {
        answer(999_999L, "답변")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(120005));

        AdminAuditLog row = onlyRow();
        assertThat(row.getTargetId()).isEqualTo("999999");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.FAILURE);
    }

    /**
     * 처리중·보류 전이는 면제다. 전이가 실제로 일어났는데도 행이 없다는 것을 함께 단언한다 —
     * 면제를 지우면 이 테스트가 깨지므로, 되살릴지는 다시 결정해야 한다.
     */
    @Test
    void 처리_상태_전이는_행을_남기지_않고_문의만_옮긴다() throws Exception {
        long inquiryId = seedInquiry(InquiryType.DELIVERY_CLAIM, "반품 접수가 안 돼요");

        changeStatus(inquiryId, "IN_PROGRESS").andExpect(status().isOk());
        changeStatus(inquiryId, "ON_HOLD").andExpect(status().isOk());

        assertThat(auditRows()).isEmpty();
        Inquiry moved = reload(inquiryId);
        assertThat(moved.getStatus()).isEqualTo(InquiryStatus.ON_HOLD);
        assertThat(moved.getAssignedAdminId()).isEqualTo(adminId);
    }

    @Test
    void 문의_조회는_행을_남기지_않는다() throws Exception {
        long inquiryId = seedInquiry(InquiryType.SERVICE_ETC, "제휴 문의드립니다");

        mockMvc.perform(bearer(get(BASE), adminToken)).andExpect(status().isOk());
        mockMvc.perform(bearer(get(BASE + "/" + inquiryId), adminToken)).andExpect(status().isOk());

        assertThat(auditRows()).isEmpty();
    }

    private ResultActions answer(long inquiryId, String body) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + inquiryId + "/answer"), adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\": \"" + body + "\"}"));
    }

    private ResultActions changeStatus(long inquiryId, String target) throws Exception {
        return mockMvc.perform(bearer(patch(BASE + "/" + inquiryId + "/status"), adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + target + "\"}"));
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

    /** 접수 경로는 api 모듈 소유라 도메인·EntityManager로 직접 심는다. */
    private long seedInquiry(InquiryType type, String content) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            Inquiry inquiry = Inquiry.Companion.receive(buyerId, type, "buyer@aechak.test", content);
            em.persist(inquiry);
            em.flush();
            return inquiry.getId();
        }));
    }

    private Inquiry reload(long inquiryId) {
        return tx.execute(txStatus -> em.createQuery("select i from Inquiry i where i.id = :id", Inquiry.class)
                .setParameter("id", inquiryId)
                .getSingleResult());
    }

    private long createOnboardedUser(String nickname, UserRole role) {
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

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
