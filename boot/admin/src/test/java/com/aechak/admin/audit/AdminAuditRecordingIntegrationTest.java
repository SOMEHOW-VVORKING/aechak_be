package com.aechak.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.SellerReviewIntegrationTestBase;
import com.aechak.domain.audit.auditlog.AdminAuditLog;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import com.aechak.webcommon.trace.TraceIdFilter;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * [통합] 운영 조치가 실제로 감사 행을 남기는지 — 그리고 <b>기록이 조치를 건드리지 않는지</b>.
 *
 * <p>행이 생겼다는 단언만으로는 부족하다. 감사 로그에서 조용히 틀리는 자리는 셋이다.
 *
 * <ul>
 *   <li>거부당한 시도가 행을 남기지 않는다 — "하려 했지만 막혔다"가 추적에서 사라진다.
 *   <li>실패 행을 남기면서 조치의 부작용이 함께 일어난다 — 실패인데 승격이 됐다.
 *   <li>조회까지 행을 남긴다 — 로그가 조회 소음으로 가득 차 조치가 묻힌다.
 * </ul>
 *
 * 그래서 이 파일의 단언은 매번 <b>행 + 대상의 실제 상태</b>를 함께 본다.
 */
class AdminAuditRecordingIntegrationTest extends SellerReviewIntegrationTestBase {

    private static final String ROSTER = "/api/v1/admin/admins";

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private FilterChainProxy adminSecurityChain;

    /**
     * 공용 베이스의 MockMvc에는 시큐리티 체인만 끼워져 있다. 추적 id는 서블릿 필터가 넣는 값이라
     * 그 체인만으로는 비고, 그러면 이 파일이 "추적 id는 원래 비는 값"을 고정하게 된다 —
     * 운영 체인과 같은 모양으로 다시 만든다(추적 필터가 시큐리티보다 앞이다).
     */
    @BeforeEach
    void rebuildMockMvcWithTraceFilter() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(new TraceIdFilter(), adminSecurityChain)
                .build();
    }

    @Test
    void 입점_승인은_성공_행을_남기고_그때의_상호명을_함께_적는다() throws Exception {
        long applicantId = createUser();
        long applicationId = seedApplication(applicantId, true);

        mockMvc.perform(bearer(post(BASE + "/" + applicationId + "/approve"), adminToken))
                .andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.SELLER_APPLICATION_APPROVED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.SELLER_APPLICATION);
        assertThat(row.getTargetId()).isEqualTo(String.valueOf(applicationId));
        assertThat(row.getTargetLabel()).isEqualTo("애착상회");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(row.getHttpStatus()).isEqualTo(204);
        assertThat(row.getReason()).isNull();
        assertThat(statusOf(applicationId)).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void 입점_거부는_운영자가_적은_사유를_행에_남긴다() throws Exception {
        long applicationId = seedApplication(createUser(), true);

        mockMvc.perform(rejectRequest(applicationId, "통장 사본이 사업자명과 다릅니다")).andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.SELLER_APPLICATION_REJECTED);
        assertThat(row.getReason()).isEqualTo("통장 사본이 사업자명과 다릅니다");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(statusOf(applicationId)).isEqualTo(ApplicationStatus.REJECTED);
    }

    /**
     * 거부당한 시도가 남아야 한다 — 책임 추적에서 "누가 무엇을 하려 했나"가 "무엇을 했나"만큼 재료다.
     * 동시에 두 번째 승인이 아무것도 바꾸지 않았음을 신청서 상태로 확인한다.
     */
    @Test
    void 이미_처리된_신청의_재승인은_실패_행을_남기고_신청서를_건드리지_않는다() throws Exception {
        long applicationId = seedApplication(createUser(), true);
        mockMvc.perform(bearer(post(BASE + "/" + applicationId + "/approve"), adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(bearer(post(BASE + "/" + applicationId + "/approve"), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(10101));

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        AdminAuditLog failure = rows.get(1);
        assertThat(failure.getAction()).isEqualTo(AdminAuditAction.SELLER_APPLICATION_APPROVED);
        assertThat(failure.getTargetId()).isEqualTo(String.valueOf(applicationId));
        assertThat(failure.getResult()).isEqualTo(AdminAuditResult.FAILURE);
        assertThat(failure.getHttpStatus()).isEqualTo(409);
        assertThat(statusOf(applicationId)).isEqualTo(ApplicationStatus.APPROVED);
    }

    /** 대상이 본문에 있는 조치 — 경로 변수로는 잡히지 않아 컨트롤러가 컨텍스트로 올린다 */
    @Test
    void 관리자_승격은_본문의_대상을_행에_남긴다() throws Exception {
        long targetUserId = createUser(UserStatus.ACTIVE);

        mockMvc.perform(bearer(post(ROSTER), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + targetUserId + "}"))
                .andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.ADMIN_PROMOTED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.ADMIN_USER);
        assertThat(row.getTargetId()).isEqualTo(String.valueOf(targetUserId));
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(roleOf(targetUserId)).isEqualTo(UserRole.ADMIN);
    }

    /**
     * 거부된 승격도 <b>대상이 채워진 채</b> 남아야 한다. 공개 식별자로 통일했다면 이 행의 대상 칸이
     * 비었을 자리다 — 거부는 애그리거트를 읽기 전이나 읽은 직후에 끊기기 때문이다.
     */
    @Test
    void 정지된_계정_승격_거부는_대상이_채워진_실패_행을_남기고_역할을_그대로_둔다() throws Exception {
        long targetUserId = createUser(UserStatus.SUSPENDED);

        mockMvc.perform(bearer(post(ROSTER), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + targetUserId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30017));

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.ADMIN_PROMOTED);
        assertThat(row.getTargetId()).isEqualTo(String.valueOf(targetUserId));
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.FAILURE);
        assertThat(row.getHttpStatus()).isEqualTo(409);
        assertThat(roleOf(targetUserId)).isEqualTo(UserRole.GENERAL);
    }

    @Test
    void 관리자_강등은_경로_변수의_대상을_행에_남긴다() throws Exception {
        long targetUserId = createUser(UserStatus.ACTIVE, UserRole.ADMIN);
        createUser(UserStatus.ACTIVE, UserRole.ADMIN); // 마지막 관리자 강등 금지(30018)를 비켜 간다

        mockMvc.perform(bearer(delete(ROSTER + "/" + targetUserId), adminToken)).andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.ADMIN_DEMOTED);
        assertThat(row.getTargetId()).isEqualTo(String.valueOf(targetUserId));
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(roleOf(targetUserId)).isEqualTo(UserRole.GENERAL);
    }

    /** 조회까지 기록하면 조치가 조회 소음에 묻힌다 — 감사 로그가 쓸모를 잃는 가장 흔한 길이다 */
    @Test
    void 조회는_행을_남기지_않는다() throws Exception {
        long applicationId = seedApplication(createUser(), true);

        mockMvc.perform(bearer(get(BASE).param("status", "SUBMITTED"), adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(bearer(get(BASE + "/" + applicationId), adminToken)).andExpect(status().isOk());
        mockMvc.perform(bearer(get(ROSTER), adminToken)).andExpect(status().isOk());
        mockMvc.perform(bearer(get("/api/v1/admin/audit-logs"), adminToken)).andExpect(status().isOk());

        assertThat(auditRows()).isEmpty();
    }

    /** 추적 id가 비면 행과 서버 로그를 맞춰 볼 수단이 없다 — 어드민에 TraceIdFilter가 등록돼야 채워진다 */
    @Test
    void 행에_추적_id와_접속_IP가_채워진다() throws Exception {
        long applicationId = seedApplication(createUser(), true);

        mockMvc.perform(rejectRequest(applicationId, "서류 재제출 요망")).andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getRequestId()).as("X-Trace-Id가 행에 들어오지 않았다").isNotBlank();
        assertThat(row.getClientIp()).as("접속 IP가 행에 들어오지 않았다").isNotBlank();
    }

    /** 클라이언트가 보낸 추적 id를 그대로 이어 쓴다 — 화면·게이트웨이의 id와 같은 값이어야 맞춰 볼 수 있다 */
    @Test
    void 요청_헤더의_추적_id를_이어_쓰고_전달받은_클라이언트_IP를_적는다() throws Exception {
        long applicationId = seedApplication(createUser(), true);

        mockMvc.perform(bearer(post(BASE + "/" + applicationId + "/approve"), adminToken)
                        .header("X-Trace-Id", "trace-from-admin-web")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                .andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getRequestId()).isEqualTo("trace-from-admin-web");
        assertThat(row.getClientIp()).isEqualTo("203.0.113.7");
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

    private ApplicationStatus statusOf(long applicationId) {
        return tx.execute(
                txStatus -> em.find(SellerApplication.class, applicationId).getStatus());
    }

    private UserRole roleOf(long userId) {
        return tx.execute(txStatus -> em.createQuery("select u.role from User u where u.id = :id", UserRole.class)
                .setParameter("id", userId)
                .getSingleResult());
    }

    private MockHttpServletRequestBuilder rejectRequest(long applicationId, String reason) {
        return bearer(post(BASE + "/" + applicationId + "/reject"), adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + reason + "\"}");
    }
}
