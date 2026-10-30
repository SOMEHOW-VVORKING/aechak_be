package com.aechak.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.AdminClaimIntegrationTestBase;
import com.aechak.domain.audit.auditlog.AdminAuditLog;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetIdKind;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.domain.order.claim.enums.ClaimStatus;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * [통합] 클레임 개입의 감사 기록을 고정한다.
 *
 * <p>개입은 셀러 처리를 덮고 분쟁을 종결하는 조치라 감사 대상이다 — 누가 어느 분쟁을 어느 근거로
 * 뒤집었는지가 남지 않으면, 나중에 그 판정을 다툴 때 참조할 기록이 없다.
 *
 * <p>이 테스트가 따로 있는 이유는 {@code AdminAuditCoverageTest}가 보지 못하는 것을 보기 때문이다.
 * 커버리지 테스트는 "애너테이션이 붙었나"까지만 보고, 기록 실패는 조치를 되돌리지 않고 ERROR 로그로만
 * 남는다 — 대상 종류와 식별자 종류의 짝이 어긋나면 조치는 성공한 채 행만 조용히 사라진다.
 * {@code CLAIM}이 {@code PUBLIC_ULID}를 선언한 것이 어드민 경로와 실제로 맞는지는 여기서만 확인된다.
 */
class AdminAuditClaimRecordingIntegrationTest extends AdminClaimIntegrationTestBase {

    /** {@code EVIDENCE_SUPPORTS_BUYER}의 label + 상세. 감사 행의 사유 칸에 이 문구가 들어간다 */
    private static final String EXPECTED_REASON = "구매자 주장에 근거가 있어요 — 사진으로 파손이 확인됨";

    @Test
    void 개입은_클레임_공개_ULID를_대상으로_적고_판정_사유를_남긴다() throws Exception {
        var seeded = seedClaim();
        rejectBySeller(seeded.claimPublicId(), "확인되지 않습니다");

        mockMvc.perform(intervention(seeded.claimPublicId())).andExpect(status().isOk());

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.CLAIM_INTERVENED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.CLAIM);
        assertThat(row.getTargetId()).isEqualTo(seeded.claimPublicId());
        assertThat(AdminAuditTargetIdKind.PUBLIC_ULID.accepts(row.getTargetId()))
                .as("클레임 대상 식별자가 공개 ULID가 아니면 성공 행이 도메인 대조에서 버려진다")
                .isTrue();
        assertThat(row.getReason())
                .as("고정 어휘 코드만 남기면 감사 화면이 다시 번역해야 하고, 상세를 버리면 판정 근거가 사라진다")
                .isEqualTo(EXPECTED_REASON);
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(row.getHttpStatus()).isEqualTo(200);
        assertThat(readClaim(seeded.claimPublicId()).getStatus()).isEqualTo(ClaimStatus.ADMIN_OVERRIDDEN);
    }

    /**
     * 사유를 조치보다 먼저 올리는 이유가 여기 있다. 환불이 움직인 건은 유스케이스가 409로 끊는데,
     * 그 시도야말로 "운영자가 무엇을 하려 했나"가 남아야 하는 자리다.
     */
    @Test
    void 개입할_수_없는_클레임의_시도도_사유가_채워진_실패_행으로_남는다() throws Exception {
        var seeded = seedClaim();
        approveBySeller(seeded.claimPublicId(), null);
        markRefunded(seeded.claimPublicId());

        mockMvc.perform(intervention(seeded.claimPublicId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(50414));

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.CLAIM_INTERVENED);
        assertThat(row.getTargetId()).isEqualTo(seeded.claimPublicId());
        assertThat(row.getReason()).isEqualTo(EXPECTED_REASON);
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.FAILURE);
        assertThat(row.getHttpStatus()).isEqualTo(409);
        assertThat(readClaim(seeded.claimPublicId()).getStatus()).isNotEqualTo(ClaimStatus.ADMIN_OVERRIDDEN);
    }

    /**
     * 모듈 게이트가 끊은 요청은 Security 필터 구간에서 끝나 DispatcherServlet에 닿지 않는다 —
     * 인터셉터를 지나가지 않으므로 행이 없다. 이것은 알려진 공백이고, 기대를 글로 고정해 둔다
     * (그 시도는 접근 로그에만 남는다).
     */
    @Test
    void 운영자가_아닌_개입_시도는_감사_행을_남기지_않는다() throws Exception {
        var seeded = seedClaim();
        rejectBySeller(seeded.claimPublicId(), "확인되지 않습니다");
        String generalToken = mintAccessToken(createUser(UserStatus.ACTIVE, UserRole.GENERAL), UserRole.GENERAL);

        mockMvc.perform(bearer(interventionBody(seeded.claimPublicId()), generalToken))
                .andExpect(status().isForbidden());

        assertThat(auditRows()).as("필터 구간에서 끊긴 시도까지 남는다면 기록 자리가 바뀐 것이다").isEmpty();
    }

    private MockHttpServletRequestBuilder intervention(String claimPublicId) {
        return bearer(interventionBody(claimPublicId), adminToken);
    }

    private MockHttpServletRequestBuilder interventionBody(String claimPublicId) {
        return post(BASE + "/" + claimPublicId + "/intervention")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {
                          "faultDecision": "SELLER_FAULT",
                          "reason": "EVIDENCE_SUPPORTS_BUYER",
                          "reasonDetail": "사진으로 파손이 확인됨",
                          "internalMemo": "재발 시 셀러 경고"
                        }
                        """);
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
}
