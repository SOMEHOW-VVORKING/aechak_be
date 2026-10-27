package com.aechak.admin.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.domain.order.order.enums.OrderStatus;
import com.aechak.domain.user.user.enums.UserRole;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * [통합] 어드민 주문 배송 모니터링 조회를 고정한다.
 *
 * 깨지면 운영자가 ① 결제 전 주문까지 보거나 ② 지연 건수와 목록의 지연 표시가 다른 기준으로 갈려
 * 어느 쪽을 믿어야 할지 모르거나 ③ 커서가 같은 시각 주문을 건너뛰어 지연 건을 영구히 못 본다.
 */
class AdminOrderMonitoringIntegrationTest extends OrderMonitoringTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(20004));
    }

    @Test
    void 일반_역할_토큰은_403이다() throws Exception {
        String generalToken = mintAccessToken(createUser(), UserRole.GENERAL);

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + generalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }

    @Test
    void 목록은_셀러_상호와_구매자_닉네임을_함께_돌려준다() throws Exception {
        var seeded = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.PAID, LocalDateTime.now().minusHours(1));
        seedShipment(seeded.orderId(), "6381-2049-1182");

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].orderId").value(seeded.publicId()))
                .andExpect(jsonPath("$.data.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.data.items[0].sellerId").value(sellerUserId))
                .andExpect(jsonPath("$.data.items[0].sellerName").value("멍멍마켓"))
                .andExpect(jsonPath("$.data.items[0].buyerNickname").exists())
                .andExpect(jsonPath("$.data.items[0].representativeItemName").value("프리미엄 관절 영양제 180정"))
                .andExpect(jsonPath("$.data.items[0].itemCount").value(1))
                // 상품금액(39,000 * 2) + 셀러배송비 3,000 - 안분 쿠폰할인 0
                .andExpect(jsonPath("$.data.items[0].totalProductAmount").value(78_000))
                .andExpect(jsonPath("$.data.items[0].payableAmount").value(81_000))
                .andExpect(jsonPath("$.data.items[0].shipment.courierName").value("CJ대한통운"))
                .andExpect(jsonPath("$.data.items[0].shipment.trackingNumber").value("6381-2049-1182"))
                .andExpect(jsonPath("$.data.items[0].delayed").value(false))
                .andExpect(jsonPath("$.data.items[0].delayReason").doesNotExist());
    }

    /**
     * 결제대기 주문도 테이블에 남는 모델이다. 목록에 섞이면 운영자가 "결제 전 장바구니"를 미발송 주문으로
     * 보고 셀러를 독촉한다.
     */
    @Test
    void 결제대기_주문은_목록에도_상세에도_없다() throws Exception {
        var pending = seedOrder(
                sellerUserId,
                "멍멍마켓",
                OrderStatus.PENDING_PAYMENT,
                LocalDateTime.now().minusDays(9));

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        mockMvc.perform(bearer(get(BASE + "/" + pending.publicId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(50000));
    }

    @Test
    void 결제대기를_상태로_지정하면_400이다() throws Exception {
        mockMvc.perform(bearer(get(BASE).param("status", "PENDING_PAYMENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));
    }

    @Test
    void 상태_셀러_기간_키워드로_좁힌다() throws Exception {
        var mine = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.SHIPPING, LocalDateTime.now().minusDays(1));
        var other = seedOrder(
                otherSellerUserId, "냥이상점", OrderStatus.PAID, LocalDateTime.now().minusDays(1));
        seedOrder(sellerUserId, "멍멍마켓", OrderStatus.PAID, LocalDateTime.now().minusDays(40));

        mockMvc.perform(bearer(get(BASE).param("status", "SHIPPING")))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].orderId").value(mine.publicId()));

        mockMvc.perform(bearer(get(BASE).param("sellerId", String.valueOf(otherSellerUserId))))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].orderId").value(other.publicId()));

        mockMvc.perform(bearer(get(BASE)
                        .param(
                                "orderedFrom",
                                LocalDateTime.now().minusDays(3).toLocalDate().toString())))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        mockMvc.perform(bearer(get(BASE).param("keyword", "냥이상점")))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].orderId").value(other.publicId()));

        mockMvc.perform(bearer(get(BASE).param("keyword", "오리목뼈")))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].orderId").value(other.publicId()));

        mockMvc.perform(bearer(get(BASE).param("keyword", mine.publicId())))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].orderId").value(mine.publicId()));
    }

    @Test
    void 역전된_주문일_범위는_400이다() throws Exception {
        mockMvc.perform(bearer(get(BASE).param("orderedFrom", "2026-10-20").param("orderedTo", "2026-10-19")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));
    }

    @Test
    void size_상한을_넘기면_400이다() throws Exception {
        mockMvc.perform(bearer(get(BASE).param("size", "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));
    }

    @Test
    void 깨진_커서는_400이다() throws Exception {
        mockMvc.perform(bearer(get(BASE).param("cursor", "%%%broken%%%")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90002));
    }

    /**
     * 같은 마이크로초에 들어온 주문 셋을 2건씩 넘긴다. 커서가 주문 시각만 실으면 두 번째 페이지가
     * 같은 시각 전부를 건너뛰거나 되돌아와 한 건이 두 번 나온다.
     */
    @Test
    void 커서는_같은_시각_주문을_건너뛰지도_중복하지도_않는다() throws Exception {
        LocalDateTime sameInstant = LocalDateTime.of(2026, 10, 20, 11, 22, 33, 444_555_000);
        var first = seedOrder(sellerUserId, "멍멍마켓", OrderStatus.PAID, sameInstant);
        var second = seedOrder(sellerUserId, "멍멍마켓", OrderStatus.PAID, sameInstant);
        var third = seedOrder(sellerUserId, "멍멍마켓", OrderStatus.PAID, sameInstant);

        JsonNode page1 = body(mockMvc.perform(bearer(get(BASE).param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn());
        assertEquals(2, page1.get("items").size(), "첫 페이지는 두 건이어야 한다");
        assertEquals(third.publicId(), page1.get("items").get(0).get("orderId").asText());
        assertEquals(second.publicId(), page1.get("items").get(1).get("orderId").asText());

        JsonNode page2 = body(mockMvc.perform(bearer(get(BASE)
                        .param("size", "2")
                        .param("cursor", page1.get("nextCursor").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andReturn());
        assertEquals(1, page2.get("items").size(), "남은 한 건만 와야 한다");
        assertEquals(first.publicId(), page2.get("items").get(0).get("orderId").asText());
    }

    /**
     * 지연 기준은 서버가 쥔다(order.dispatch.sla-days=3 / order.delivery.sla-days=7).
     * 발송 전 상태만 발송 기한으로 재고, 배송중은 더 긴 기한으로 잰다.
     */
    @Test
    void 지연_신호는_상태별로_다른_기한으로_판정한다() throws Exception {
        var overdueDispatch = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusDays(4));
        seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusDays(2));
        var shippingFresh = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.SHIPPING, LocalDateTime.now().minusDays(4));
        var shippingStalled = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.SHIPPING, LocalDateTime.now().minusDays(9));
        seedOrder(
                sellerUserId,
                "멍멍마켓",
                OrderStatus.PURCHASE_CONFIRMED,
                LocalDateTime.now().minusDays(60));

        mockMvc.perform(bearer(get(BASE + "/" + overdueDispatch.publicId())))
                .andExpect(jsonPath("$.data.order.delayed").value(true))
                .andExpect(jsonPath("$.data.order.delayReason").value("DISPATCH_OVERDUE"));
        mockMvc.perform(bearer(get(BASE + "/" + shippingFresh.publicId())))
                .andExpect(jsonPath("$.data.order.delayed").value(false));
        mockMvc.perform(bearer(get(BASE + "/" + shippingStalled.publicId())))
                .andExpect(jsonPath("$.data.order.delayed").value(true))
                .andExpect(jsonPath("$.data.order.delayReason").value("DELIVERY_OVERDUE"));

        mockMvc.perform(bearer(get(BASE).param("delayedOnly", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    /**
     * 카드의 지연 건수(집계 SQL)와 목록 행의 지연 표시(애플리케이션 판정)는 같은 기준이어야 한다.
     * 두 술어가 각자 사는 구조라 어긋나면 운영자가 "지연 3건"을 보고 목록에서 2건만 찾는다.
     */
    @Test
    void 카드의_지연_건수는_목록이_표시한_지연_건수와_같다() throws Exception {
        seedOrder(sellerUserId, "멍멍마켓", OrderStatus.PAID, LocalDateTime.now().minusDays(5));
        seedOrder(
                sellerUserId,
                "멍멍마켓",
                OrderStatus.DISPATCH_PENDING,
                LocalDateTime.now().minusDays(4));
        seedOrder(
                otherSellerUserId,
                "냥이상점",
                OrderStatus.SHIPPING,
                LocalDateTime.now().minusDays(8));
        seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.PREPARING, LocalDateTime.now().minusHours(2));
        seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.DELIVERED, LocalDateTime.now().minusDays(30));

        JsonNode metrics = body(mockMvc.perform(bearer(get(BASE + "/metrics")))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode listed = body(mockMvc.perform(bearer(get(BASE).param("size", "100")))
                .andExpect(status().isOk())
                .andReturn());

        long delayedRows = 0;
        for (JsonNode row : listed.get("items")) {
            if (row.get("delayed").asBoolean()) {
                delayedRows++;
            }
        }
        assertEquals(3, delayedRows, "지연 행은 셋이어야 한다 — 발송 지연 둘 + 배송 정체 하나");
        assertEquals(delayedRows, metrics.get("delayedCount").asLong(), "집계 술어와 행 판정이 같은 기준이어야 한다");
        assertEquals(3, metrics.get("awaitingDispatchCount").asLong(), "결제완료·상품준비·발송대기를 모두 센다");
        assertEquals(1, metrics.get("shippingCount").asLong());
        // 주문 다섯 건 × (39,000 × 2 + 3,000)
        assertEquals(405_000, metrics.get("totalPaymentAmount").asLong(), "조회 범위 결제액은 상품금액 + 배송비 - 쿠폰할인");
    }

    /** 카드는 상태로 좁히지 않는다 — 좁히면 카드 넷 중 셋이 0이 되어 축으로서 쓸모가 없다. */
    @Test
    void 카드는_셀러와_기간으로만_좁힌다() throws Exception {
        seedOrder(sellerUserId, "멍멍마켓", OrderStatus.PAID, LocalDateTime.now().minusDays(1));
        seedOrder(
                otherSellerUserId,
                "냥이상점",
                OrderStatus.SHIPPING,
                LocalDateTime.now().minusDays(1));
        seedOrder(sellerUserId, "멍멍마켓", OrderStatus.PAID, LocalDateTime.now().minusDays(40));

        mockMvc.perform(bearer(get(BASE + "/metrics").param("sellerId", String.valueOf(sellerUserId))))
                .andExpect(jsonPath("$.data.awaitingDispatchCount").value(2))
                .andExpect(jsonPath("$.data.shippingCount").value(0));

        mockMvc.perform(bearer(get(BASE + "/metrics")
                        .param(
                                "orderedFrom",
                                LocalDateTime.now().minusDays(3).toLocalDate().toString())))
                .andExpect(jsonPath("$.data.awaitingDispatchCount").value(1))
                .andExpect(jsonPath("$.data.shippingCount").value(1))
                .andExpect(jsonPath("$.data.totalPaymentAmount").value(162_000));
    }

    @Test
    void 주문이_없는_범위의_카드는_0이다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/metrics")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.awaitingDispatchCount").value(0))
                .andExpect(jsonPath("$.data.shippingCount").value(0))
                .andExpect(jsonPath("$.data.delayedCount").value(0))
                .andExpect(jsonPath("$.data.totalPaymentAmount").value(0));
    }

    /** 타임라인의 재료는 상태 전이 이력이다 — 전이 시각 오름차순이어야 화면이 단계를 순서대로 그린다. */
    @Test
    void 상세는_품목_전문과_상태_전이_이력을_순서대로_돌려준다() throws Exception {
        var seeded = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.SHIPPING, LocalDateTime.now().minusDays(2));
        seedShipment(seeded.orderId(), "2210-7741-8890");

        mockMvc.perform(bearer(get(BASE + "/" + seeded.publicId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.orderId").value(seeded.publicId()))
                .andExpect(jsonPath("$.data.orderGroupId").exists())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productName").value("프리미엄 관절 영양제 180정"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(39_000))
                .andExpect(jsonPath("$.data.items[0].itemStatus").value("ORDERED"))
                .andExpect(jsonPath("$.data.statusHistory.length()").value(4))
                .andExpect(jsonPath("$.data.statusHistory[0].fromStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.statusHistory[0].toStatus").value("PAID"))
                .andExpect(jsonPath("$.data.statusHistory[0].triggeredBy").value("SYSTEM"))
                .andExpect(jsonPath("$.data.statusHistory[3].toStatus").value("SHIPPING"))
                .andExpect(jsonPath("$.data.nudges.length()").value(0));
    }

    /** 배송 진행 감시에 수령인명·연락처·주소는 쓰이지 않는다 — 어드민에 PII 복호 경로를 새로 열지 않는다. */
    @Test
    void 응답에_수령인_연락처_주소를_싣지_않는다() throws Exception {
        var seeded = seedOrder(
                sellerUserId, "멍멍마켓", OrderStatus.PAID, LocalDateTime.now().minusHours(1));

        MvcResult result = mockMvc.perform(bearer(get(BASE + "/" + seeded.publicId())))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        for (String forbidden :
                new String[] {"enc-name", "enc-phone", "06236", "서울 강남구", "recipientName", "contactNumber"}) {
            assertEquals(false, json.contains(forbidden), forbidden + "이(가) 응답에 실려서는 안 된다");
        }
    }

    @Test
    void 없는_주문_상세는_404다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/01JXXXXXXXXXXXXXXXXXXXXXXX")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(50000));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return MAPPER.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
