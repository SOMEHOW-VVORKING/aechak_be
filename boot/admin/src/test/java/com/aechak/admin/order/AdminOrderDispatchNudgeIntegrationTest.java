package com.aechak.admin.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.application.email.port.EmailSender;
import com.aechak.domain.order.nudge.OrderDispatchNudge;
import com.aechak.domain.order.order.enums.OrderStatus;
import com.aechak.domain.user.user.enums.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * [통합] 셀러 발송 독촉을 고정한다.
 *
 * 깨지면 ① 이미 발송된 주문에 독촉이 쌓여 셀러가 틀린 지적을 받거나 ② 쿨다운이 풀려 운영자의
 * 연타가 그대로 이력이 되거나 ③ 응답이 보내지도 않은 알림을 보냈다고 말한다.
 */
class AdminOrderDispatchNudgeIntegrationTest extends OrderMonitoringTestBase {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 이 경로가 "기록만"인 근거를 고정한다. 어드민 실행 모듈은 발송 어댑터(:ses-client)를 의존하지
     * 않아 EmailSender 구현이 없다 — 응답에 발송을 주장하는 필드를 두면 거짓이 된다.
     * 발송을 붙이려면 이 단언이 먼저 깨지고, 그때 응답 계약을 함께 고쳐야 한다.
     */
    @Test
    void 어드민에는_발송_수단이_없다() {
        assertEquals(
                0,
                applicationContext.getBeanNamesForType(EmailSender.class).length,
                "EmailSender 구현이 조립되면 독촉이 실제로 보낼 수 있게 되므로 응답 계약을 다시 정해야 한다");
    }

    @Test
    void 독촉은_기록을_남기고_발송을_주장하지_않는다() throws Exception {
        var seeded = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusDays(4));

        mockMvc.perform(nudge(seeded.publicId(), "{\"memo\":\"송장 등록 부탁드립니다.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nudgeId").exists())
                .andExpect(jsonPath("$.data.adminUserId").value(adminUserId))
                .andExpect(jsonPath("$.data.memo").value("송장 등록 부탁드립니다."))
                .andExpect(jsonPath("$.data.nudgedAt").exists())
                .andExpect(jsonPath("$.data.notified").doesNotExist())
                .andExpect(jsonPath("$.data.emailSent").doesNotExist())
                .andExpect(jsonPath("$.data.sent").doesNotExist());

        List<OrderDispatchNudge> rows = nudgesOf(seeded.orderId());
        assertEquals(1, rows.size(), "기록이 한 행 남아야 한다");
        assertEquals("송장 등록 부탁드립니다.", rows.get(0).getMemo());
        assertEquals(adminUserId, rows.get(0).getAdminUserId());

        mockMvc.perform(bearer(get(BASE + "/" + seeded.publicId())))
                .andExpect(jsonPath("$.data.nudges.length()").value(1))
                .andExpect(jsonPath("$.data.nudges[0].memo").value("송장 등록 부탁드립니다."));
    }

    @Test
    void 공백만_적은_메모는_없는_것으로_접는다() throws Exception {
        var seeded = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.PAID, LocalDateTime.now().minusDays(4));

        mockMvc.perform(nudge(seeded.publicId(), "{\"memo\":\"   \"}")).andExpect(status().isCreated());

        assertNull(nudgesOf(seeded.orderId()).get(0).getMemo(), "빈 문자열과 null이 섞이면 이력 표시가 갈린다");
    }

    @Test
    void 메모_없이도_독촉할_수_있다() throws Exception {
        var seeded = seedOrder(
                sellerUserId,
                "멍멍마켓",
                OrderStatus.DISPATCH_PENDING,
                LocalDateTime.now().minusDays(4));

        mockMvc.perform(nudge(seeded.publicId(), "{}")).andExpect(status().isCreated());

        assertEquals(1, nudgesOf(seeded.orderId()).size());
    }

    /** 이미 송장이 붙은 주문에 보내는 독촉은 셀러에게 틀린 지적이다 — 거절하고 기록도 남기지 않는다. */
    @Test
    void 발송된_주문의_독촉은_409이고_기록이_남지_않는다() throws Exception {
        var shipping = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.SHIPPING, LocalDateTime.now().minusDays(9));

        mockMvc.perform(nudge(shipping.publicId(), "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(50500));

        assertTrue(nudgesOf(shipping.orderId()).isEmpty(), "거절된 요청이 기록을 남기면 이력이 거짓이 된다");
    }

    @Test
    void 구매확정된_주문의_독촉도_409다() throws Exception {
        var confirmed = seedOrder(
                sellerUserId,
                "멍멍마켓",
                OrderStatus.PURCHASE_CONFIRMED,
                LocalDateTime.now().minusDays(30));

        mockMvc.perform(nudge(confirmed.publicId(), "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(50500));

        assertTrue(nudgesOf(confirmed.orderId()).isEmpty());
    }

    @Test
    void 쿨다운_안의_재독촉은_409이고_기록이_늘지_않는다() throws Exception {
        var seeded = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusDays(4));

        mockMvc.perform(nudge(seeded.publicId(), "{\"memo\":\"첫 독촉\"}")).andExpect(status().isCreated());
        mockMvc.perform(nudge(seeded.publicId(), "{\"memo\":\"두 번째\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(50501));

        List<OrderDispatchNudge> rows = nudgesOf(seeded.orderId());
        assertEquals(1, rows.size(), "쿨다운에 걸린 요청이 기록을 남기면 쿨다운이 의미가 없다");
        assertEquals("첫 독촉", rows.get(0).getMemo());
    }

    /** 쿨다운(기본 6시간)이 지나면 다시 누를 수 있어야 한다 — 영구 1회면 장기 미발송에 손을 못 쓴다. */
    @Test
    void 쿨다운이_지나면_다시_독촉할_수_있다() throws Exception {
        var seeded = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusDays(4));

        mockMvc.perform(nudge(seeded.publicId(), "{\"memo\":\"첫 독촉\"}")).andExpect(status().isCreated());
        backdateNudges(seeded.orderId(), LocalDateTime.now().minusHours(7));

        mockMvc.perform(nudge(seeded.publicId(), "{\"memo\":\"두 번째\"}")).andExpect(status().isCreated());

        assertEquals(2, nudgesOf(seeded.orderId()).size());
    }

    @Test
    void 메모가_상한을_넘으면_400이고_기록이_남지_않는다() throws Exception {
        var seeded = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusDays(4));

        mockMvc.perform(nudge(seeded.publicId(), "{\"memo\":\"" + "가".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));

        assertTrue(nudgesOf(seeded.orderId()).isEmpty());
    }

    @Test
    void 없는_주문의_독촉은_404다() throws Exception {
        mockMvc.perform(nudge("01JXXXXXXXXXXXXXXXXXXXXXXX", "{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(50000));
    }

    @Test
    void 결제대기_주문의_독촉은_409다() throws Exception {
        var pending = seedOrder(
                sellerUserId,
                "멍멍마켓",
                OrderStatus.PENDING_PAYMENT,
                LocalDateTime.now().minusDays(4));

        mockMvc.perform(nudge(pending.publicId(), "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(50500));

        assertTrue(nudgesOf(pending.orderId()).isEmpty(), "결제 전 주문은 셀러가 할 일이 없다");
    }

    @Test
    void 일반_역할_토큰의_독촉은_403이고_기록이_남지_않는다() throws Exception {
        var seeded = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusDays(4));
        String generalToken = mintAccessToken(createUser(), UserRole.GENERAL);

        mockMvc.perform(post(BASE + "/" + seeded.publicId() + "/dispatch-nudges")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));

        assertTrue(nudgesOf(seeded.orderId()).isEmpty());
    }

    private MockHttpServletRequestBuilder nudge(String orderPublicId, String body) {
        return bearer(post(BASE + "/" + orderPublicId + "/dispatch-nudges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<OrderDispatchNudge> nudgesOf(long orderId) {
        return tx.execute(txStatus -> em.createQuery(
                        "select n from OrderDispatchNudge n where n.orderId = :orderId order by n.id asc",
                        OrderDispatchNudge.class)
                .setParameter("orderId", orderId)
                .getResultList());
    }

    private void backdateNudges(long orderId, LocalDateTime at) {
        tx.executeWithoutResult(txStatus -> em.createNativeQuery(
                        "update order_dispatch_nudges set created_at = :at where order_id = :id")
                .setParameter("at", at)
                .setParameter("id", orderId)
                .executeUpdate());
    }
}
