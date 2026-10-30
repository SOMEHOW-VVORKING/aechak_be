package com.aechak.admin.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.domain.audit.auditlog.AdminAuditLog;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.domain.order.order.enums.OrderStatus;
import com.aechak.webcommon.trace.TraceIdFilter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * [통합] 셀러 발송 독촉이 감사 행을 남기는지.
 *
 * <p>독촉은 전용 표(order_dispatch_nudges)에도 남지만 감사 로그에 함께 기록한다 — 클레임 개입과
 * 같은 판단이다. 운영자가 어느 셀러를 언제 재촉했는지는 분쟁에서 근거가 되고, 운영 조치를 한 자리에서
 * 훑는 것이 감사 조회의 목적이기 때문이다.
 *
 * <p>쿨다운에 막힌 시도까지 단언하는 이유는 그것이 "운영자가 보고 또 눌렀다"는 사실이기 때문이다.
 */
class AdminAuditOrderNudgeRecordingIntegrationTest extends OrderMonitoringTestBase {

    @Autowired
    private WebApplicationContext auditWebContext;

    @Autowired
    private FilterChainProxy auditSecurityChain;

    private MockMvc tracedMockMvc;

    /** 베이스의 MockMvc에는 TraceIdFilter가 없다 — 감사 행의 추적 id를 보려면 그 필터가 앞에 있어야 한다. */
    @BeforeEach
    void setUpTracedMockMvc() {
        tracedMockMvc = MockMvcBuilders.webAppContextSetup(auditWebContext)
                .addFilters(new TraceIdFilter(), auditSecurityChain)
                .build();
    }

    @Test
    void 발송_독촉은_메모와_함께_성공_행을_남긴다() throws Exception {
        var seeded = seedOrder(sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusDays(4));

        nudge(seeded.publicId(), "{\"memo\": \"고객 문의가 두 번 들어왔습니다\"}")
                .andExpect(status().isCreated());

        AdminAuditLog row = onlyRow();
        assertThat(row.getActorUserId()).isEqualTo(adminUserId);
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.ORDER_DISPATCH_NUDGED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.ORDER);
        // 어드민 경로가 주문을 공개 ULID로 가리키므로 행에도 그 값이 들어간다
        assertThat(row.getTargetId()).isEqualTo(seeded.publicId());
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(row.getHttpStatus()).isEqualTo(201);
        assertThat(row.getRequestId()).as("X-Trace-Id가 행에 들어오지 않았다").isNotBlank();
        assertThat(row.getReason()).isEqualTo("고객 문의가 두 번 들어왔습니다");
    }

    /** 메모는 선택이다 — 사유 없이 누른 독촉도 기록으로 남고 사유 칸만 빈다 */
    @Test
    void 메모_없는_독촉도_사유가_빈_성공_행을_남긴다() throws Exception {
        var seeded = seedOrder(sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusDays(4));

        nudge(seeded.publicId(), "{}").andExpect(status().isCreated());

        assertThat(onlyRow().getReason()).isNull();
    }

    /** 쿨다운에 막힌 시도도 남는다 — 메모를 조치보다 먼저 올리기 때문에 실패 행에도 사유가 실린다 */
    @Test
    void 쿨다운에_막힌_재독촉도_실패_행을_남긴다() throws Exception {
        var seeded = seedOrder(sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusDays(4));
        nudge(seeded.publicId(), "{\"memo\": \"첫 독촉\"}").andExpect(status().isCreated());

        nudge(seeded.publicId(), "{\"memo\": \"두 번째 독촉\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(50501));

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(2);
        AdminAuditLog failure = rows.get(1);
        assertThat(failure.getAction()).isEqualTo(AdminAuditAction.ORDER_DISPATCH_NUDGED);
        assertThat(failure.getTargetId()).isEqualTo(seeded.publicId());
        assertThat(failure.getResult()).isEqualTo(AdminAuditResult.FAILURE);
        assertThat(failure.getHttpStatus()).isEqualTo(409);
        assertThat(failure.getReason()).isEqualTo("두 번째 독촉");
    }

    /** 없는 주문을 집은 시도도 요청이 들고 온 식별자와 함께 남는다 */
    @Test
    void 없는_주문_독촉_시도도_실패_행으로_남는다() throws Exception {
        nudge("01JXXXXXXXXXXXXXXXXXXXXXXX", "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(50000));

        AdminAuditLog row = onlyRow();
        assertThat(row.getTargetId()).isEqualTo("01JXXXXXXXXXXXXXXXXXXXXXXX");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.FAILURE);
    }

    @Test
    void 주문_조회는_행을_남기지_않는다() throws Exception {
        var seeded = seedOrder(sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusDays(4));

        tracedMockMvc.perform(bearer(get(BASE))).andExpect(status().isOk());
        tracedMockMvc.perform(bearer(get(BASE + "/" + seeded.publicId()))).andExpect(status().isOk());

        assertThat(auditRows()).isEmpty();
    }

    private ResultActions nudge(String orderPublicId, String body) throws Exception {
        return tracedMockMvc.perform(bearer(post(BASE + "/" + orderPublicId + "/dispatch-nudges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)));
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
