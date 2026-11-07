package com.aechak.admin.seller;

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
import com.aechak.domain.product.category.Category;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.BusinessType;
import com.aechak.domain.seller.sanction.SellerSanction;
import com.aechak.domain.seller.sanction.enums.SellerSanctionAction;
import com.aechak.domain.seller.seller.Seller;
import com.aechak.domain.seller.seller.enums.SellerStatus;
import com.aechak.domain.settlement.settlement.Settlement;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.webcommon.trace.TraceIdFilter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 어드민 셀러 계정 관리 API 통합 테스트 — 권한 경계, 목록 필터·페이징, 상태 전이의 금지 조합,
 * 메모 경로, 감사 기록을 HTTP로 검증한다.
 *
 * <p>거부된 요청이 상태도 이력도 건드리지 않는지까지 본다 — 반쪽 적용이 남으면 분쟁에서 설명할 수 없다.
 * MockMvc를 운영 체인과 같은 모양(추적 필터가 시큐리티보다 앞)으로 만들어 감사 행의 추적 id도 함께 검증한다.
 */
class AdminSellerIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/sellers";

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
                .addFilters(new TraceIdFilter(), securityFilterChain)
                .build();
        actorId = createUser();
        actorToken = mintAccessToken(actorId);
    }

    // --- 권한 경계 ---

    @Test
    void 토큰_없이_목록을_조회하면_401_20004_를_반환한다() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(20004));
    }

    @Test
    void 일반_GENERAL_토큰은_목록_접근이_403_20011_로_막힌다() throws Exception {
        long memberId = createUser();

        mockMvc.perform(bearer(get(BASE), mintAccessToken(memberId, UserRole.GENERAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }

    @Test
    void 일반_GENERAL_토큰은_정지도_403_20011_로_막히고_상태가_바뀌지_않는다() throws Exception {
        long sellerId = seedSeller("애착상회");

        mockMvc.perform(bearer(
                                post(BASE + "/" + sellerId + "/suspensions"),
                                mintAccessToken(sellerId, UserRole.GENERAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suspendBody("REPEATED_REPORT", null, 7, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));

        assertThat(statusOf(sellerId)).isEqualTo(SellerStatus.ACTIVE);
        assertThat(sanctionsOf(sellerId)).isEmpty();
    }

    // --- 목록 ---

    @Test
    void 목록은_셀러_식별자_역순으로_주고_전체_수를_함께_준다() throws Exception {
        long first = seedSeller("첫상회");
        long second = seedSeller("둘째상회");

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].sellerId").value(second))
                .andExpect(jsonPath("$.data.items[0].storeName").value("둘째상회"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].statusChangedAt").exists())
                .andExpect(jsonPath("$.data.items[0].suspensionUntil").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].sellerId").value(first));
    }

    @Test
    void 목록_행은_상품_수와_누적_주문_수와_미정산_보유_여부를_채운다() throws Exception {
        long withData = seedSeller("데이터상회");
        seedProducts(withData, 3);
        seedPendingSettlement(withData);
        setConfirmedOrderCount(withData, 42);
        long empty = seedSeller("빈상회");

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sellerId").value(empty))
                .andExpect(jsonPath("$.data.items[0].productCount").value(0))
                .andExpect(jsonPath("$.data.items[0].cumulativeOrderCount").value(0))
                .andExpect(jsonPath("$.data.items[0].hasUnsettledAmount").value(false))
                .andExpect(jsonPath("$.data.items[1].sellerId").value(withData))
                .andExpect(jsonPath("$.data.items[1].productCount").value(3))
                .andExpect(jsonPath("$.data.items[1].cumulativeOrderCount").value(42))
                .andExpect(jsonPath("$.data.items[1].hasUnsettledAmount").value(true));
    }

    @Test
    void 신청서가_있는_셀러는_사업자_유형과_가린_대표자명을_받는다() throws Exception {
        long sellerId = seedSeller("법인상회");
        seedApplication(sellerId, BusinessType.CORPORATE, "박정희");
        long noApplication = seedSeller("신청서없는상회");

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sellerId").value(noApplication))
                .andExpect(jsonPath("$.data.items[0].businessType").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].representativeNameMasked").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].sellerId").value(sellerId))
                .andExpect(jsonPath("$.data.items[1].businessType").value("CORPORATE"))
                .andExpect(jsonPath("$.data.items[1].representativeNameMasked").value("박*희"));
    }

    @Test
    void 상태_필터는_그_상태의_셀러만_준다() throws Exception {
        long suspended = seedSeller("정지상회");
        suspend(suspended, "REPEATED_REPORT", null, 7, null).andExpect(status().isNoContent());
        seedSeller("정상상회");

        mockMvc.perform(bearer(get(BASE).param("status", "SUSPENDED"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].sellerId").value(suspended))
                .andExpect(jsonPath("$.data.items[0].suspensionUntil").exists());
    }

    @Test
    void 판매자_유형_필터는_사업자와_개인을_가르고_신청서가_없는_셀러는_빼낸다() throws Exception {
        long corporate = seedSeller("법인상회");
        seedApplication(corporate, BusinessType.CORPORATE, "박정희");
        long soleProprietor = seedSeller("개인사업자상회");
        seedApplication(soleProprietor, BusinessType.SOLE_PROPRIETORSHIP, "이수");
        long personal = seedSeller("개인상회");
        seedApplication(personal, BusinessType.PERSONAL_GENERAL, "정은");
        seedSeller("신청서없는상회");

        mockMvc.perform(bearer(get(BASE).param("sellerType", "BUSINESS"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[0].sellerId").value(soleProprietor))
                .andExpect(jsonPath("$.data.items[1].sellerId").value(corporate));

        mockMvc.perform(bearer(get(BASE).param("sellerType", "INDIVIDUAL"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].sellerId").value(personal))
                // 두 글자 이름은 성만 남긴다
                .andExpect(jsonPath("$.data.items[0].representativeNameMasked").value("정*"));
    }

    @Test
    void 상호명_검색은_부분일치로_찾는다() throws Exception {
        long coco = seedSeller("코코상점");
        seedSeller("멍멍이집");

        mockMvc.perform(bearer(get(BASE).param("keyword", "코코"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].sellerId").value(coco));
    }

    @Test
    void 알_수_없는_상태_유형_필터는_400이_아니라_전체_목록을_준다() throws Exception {
        seedSeller("정상상회");

        mockMvc.perform(bearer(
                        get(BASE)
                                .param("status", "오타")
                                .param("sellerType", "오타")
                                .param("size", "999")
                                .param("page", "-3"),
                        actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void 페이지를_넘기면_중복도_누락도_없다() throws Exception {
        long first = seedSeller("1번상회");
        long second = seedSeller("2번상회");
        long third = seedSeller("3번상회");

        mockMvc.perform(bearer(get(BASE).param("page", "0").param("size", "2"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].sellerId").value(third))
                .andExpect(jsonPath("$.data.items[1].sellerId").value(second));

        mockMvc.perform(bearer(get(BASE).param("page", "1").param("size", "2"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].sellerId").value(first));
    }

    // --- 상세 ---

    @Test
    void 상세는_현재_정지_사유와_메모와_제재_이력을_준다() throws Exception {
        long sellerId = seedSeller("정지상회");
        suspend(sellerId, "FALSE_PRODUCT_INFO", "원산지 표기 불일치", 3, "CS 1024").andExpect(status().isNoContent());

        mockMvc.perform(bearer(get(BASE + "/" + sellerId), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seller.sellerId").value(sellerId))
                .andExpect(jsonPath("$.data.seller.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.seller.suspensionUntil").exists())
                .andExpect(jsonPath("$.data.suspensionReasonCode").value("FALSE_PRODUCT_INFO"))
                .andExpect(jsonPath("$.data.suspensionReason").value("상품 정보 허위 기재 — 원산지 표기 불일치"))
                .andExpect(jsonPath("$.data.internalMemo").value("CS 1024"))
                .andExpect(jsonPath("$.data.sanctions.length()").value(1))
                .andExpect(jsonPath("$.data.sanctions[0].action").value("SUSPEND"))
                .andExpect(jsonPath("$.data.sanctions[0].reasonCode").value("FALSE_PRODUCT_INFO"))
                .andExpect(jsonPath("$.data.sanctions[0].reasonDetail").value("원산지 표기 불일치"))
                .andExpect(jsonPath("$.data.sanctions[0].effectiveUntil").exists())
                .andExpect(jsonPath("$.data.sanctions[0].memo").value("CS 1024"))
                .andExpect(jsonPath("$.data.sanctions[0].actedByAdminId").value(actorId));
    }

    @Test
    void 정지_중이_아닌_셀러의_상세는_사유_칸이_비어_있다() throws Exception {
        long sellerId = seedSeller("정상상회");

        mockMvc.perform(bearer(get(BASE + "/" + sellerId), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspensionReasonCode").doesNotExist())
                .andExpect(jsonPath("$.data.suspensionReason").doesNotExist())
                .andExpect(jsonPath("$.data.sanctions.length()").value(0));
    }

    @Test
    void 셀러가_아닌_식별자의_상세는_404_10000_을_반환한다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/999999"), actorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(10000));
    }

    // --- 판매정지 ---

    @Test
    void 정지는_직전_상태를_보관하고_해제_예정_시각과_이력을_남긴다() throws Exception {
        long sellerId = seedSeller("정지대상상회");

        suspend(sellerId, "REPEATED_REPORT", "신고 5건 누적", 7, null).andExpect(status().isNoContent());

        assertThat(statusOf(sellerId)).isEqualTo(SellerStatus.SUSPENDED);
        Seller seller = sellerOf(sellerId);
        assertThat(seller.getPreviousStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(seller.getSuspensionReason()).isEqualTo("신고 누적 — 신고 5건 누적");
        assertThat(seller.getSuspensionUntil()).isAfter(LocalDateTime.now().plusDays(6));
        List<SellerSanction> history = sanctionsOf(sellerId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getAction()).isEqualTo(SellerSanctionAction.SUSPEND);
        assertThat(history.get(0).getEffectiveUntil()).isEqualTo(seller.getSuspensionUntil());
        assertThat(history.get(0).getActedByAdminId()).isEqualTo(actorId);
    }

    @Test
    void 기간을_생략한_정지는_해제_예정_시각이_비어_있다() throws Exception {
        long sellerId = seedSeller("무기한상회");

        suspend(sellerId, "ABNORMAL_TRANSACTION", null, null, null).andExpect(status().isNoContent());

        assertThat(sellerOf(sellerId).getSuspensionUntil()).isNull();
        assertThat(sanctionsOf(sellerId).get(0).getEffectiveUntil()).isNull();
    }

    @Test
    void 이미_정지된_셀러의_재정지는_409_10007_이고_이력이_늘지_않는다() throws Exception {
        long sellerId = seedSeller("정지상회");
        suspend(sellerId, "REPEATED_REPORT", null, 7, null).andExpect(status().isNoContent());

        suspend(sellerId, "ABNORMAL_TRANSACTION", null, 30, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(10007));

        assertThat(sanctionsOf(sellerId)).hasSize(1);
        assertThat(sellerOf(sellerId).getSuspensionReason()).isEqualTo("신고 누적");
    }

    @Test
    void 탈퇴한_셀러의_정지는_409_10009_이고_상태도_이력도_건드리지_않는다() throws Exception {
        long sellerId = seedSeller("탈퇴상회");
        setStatus(sellerId, SellerStatus.WITHDRAWN);

        suspend(sellerId, "REPEATED_REPORT", null, 7, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(10009));

        assertThat(statusOf(sellerId)).isEqualTo(SellerStatus.WITHDRAWN);
        assertThat(sanctionsOf(sellerId)).isEmpty();
    }

    @Test
    void 기타_사유에_상세를_적지_않으면_400_10010_이다() throws Exception {
        long sellerId = seedSeller("정상상회");

        suspend(sellerId, "OTHER", "   ", 7, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10010));

        assertThat(statusOf(sellerId)).isEqualTo(SellerStatus.ACTIVE);
        assertThat(sanctionsOf(sellerId)).isEmpty();
    }

    @Test
    void 사유_어휘가_없으면_400이고_셀러를_건드리지_않는다() throws Exception {
        long sellerId = seedSeller("정상상회");

        mockMvc.perform(bearer(post(BASE + "/" + sellerId + "/suspensions"), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":null,\"durationDays\":7}"))
                .andExpect(status().isBadRequest());

        assertThat(statusOf(sellerId)).isEqualTo(SellerStatus.ACTIVE);
    }

    // --- 해제 ---

    @Test
    void 해제는_정지_직전_상태로_되돌리고_해제_행을_남긴다() throws Exception {
        long sellerId = seedSeller("해제대상상회");
        suspend(sellerId, "REPEATED_REPORT", null, 7, null).andExpect(status().isNoContent());

        release(sellerId, "소명 접수, 조치 철회").andExpect(status().isNoContent());

        Seller seller = sellerOf(sellerId);
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(seller.getPreviousStatus()).isNull();
        assertThat(seller.getSuspensionReason()).isNull();
        assertThat(seller.getSuspensionUntil()).isNull();
        List<SellerSanction> history = sanctionsOf(sellerId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getAction()).isEqualTo(SellerSanctionAction.RELEASE);
        assertThat(history.get(0).getReasonCode()).isNull();
        assertThat(history.get(0).getReasonDetail()).isEqualTo("소명 접수, 조치 철회");
    }

    @Test
    void 정지가_아닌_셀러의_해제는_409_10008_이고_이력이_남지_않는다() throws Exception {
        long sellerId = seedSeller("정상상회");

        release(sellerId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(10008));

        assertThat(statusOf(sellerId)).isEqualTo(SellerStatus.ACTIVE);
        assertThat(sanctionsOf(sellerId)).isEmpty();
    }

    // --- 메모 ---

    @Test
    void 메모_저장은_제재_없이_메모만_바꾸고_이력을_남기지_않는다() throws Exception {
        long sellerId = seedSeller("메모상회");

        saveMemo(sellerId, "  CS 1024 확인  ").andExpect(status().isNoContent());

        assertThat(sellerOf(sellerId).getAdminMemo()).isEqualTo("CS 1024 확인");
        assertThat(statusOf(sellerId)).isEqualTo(SellerStatus.ACTIVE);
        assertThat(sanctionsOf(sellerId)).isEmpty();
    }

    @Test
    void 빈_메모는_메모를_지운다() throws Exception {
        long sellerId = seedSeller("메모상회");
        saveMemo(sellerId, "CS 1024").andExpect(status().isNoContent());

        saveMemo(sellerId, "   ").andExpect(status().isNoContent());

        assertThat(sellerOf(sellerId).getAdminMemo()).isNull();
    }

    /** 정지 대화상자를 메모 없이 확인한 것이 기존 메모를 날리면 운영 기록이 조용히 사라진다. */
    @Test
    void 메모를_빼고_정지하면_기존_메모가_남는다() throws Exception {
        long sellerId = seedSeller("메모상회");
        saveMemo(sellerId, "CS 1024").andExpect(status().isNoContent());

        suspend(sellerId, "REPEATED_REPORT", null, 7, null).andExpect(status().isNoContent());

        assertThat(sellerOf(sellerId).getAdminMemo()).isEqualTo("CS 1024");
        assertThat(sanctionsOf(sellerId).get(0).getMemo()).isNull();
    }

    // --- 감사 기록 ---

    @Test
    void 정지는_성공_행에_상호명과_고정_어휘_사유를_남긴다() throws Exception {
        long sellerId = seedSeller("감사상회");

        suspend(sellerId, "REPEATED_REPORT", "신고 5건", 7, null).andExpect(status().isNoContent());

        AdminAuditLog row = onlyAuditRow();
        assertThat(row.getActorUserId()).isEqualTo(actorId);
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.SELLER_SUSPENDED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.SELLER);
        assertThat(row.getTargetId()).isEqualTo(String.valueOf(sellerId));
        assertThat(row.getTargetLabel()).isEqualTo("감사상회");
        assertThat(row.getReason()).isEqualTo("신고 누적 — 신고 5건");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(row.getHttpStatus()).isEqualTo(204);
        assertThat(row.getRequestId()).as("X-Trace-Id가 행에 들어오지 않았다").isNotBlank();
    }

    @Test
    void 해제와_메모_저장도_각자의_조치로_남는다() throws Exception {
        long sellerId = seedSeller("감사상회");
        suspend(sellerId, "REPEATED_REPORT", null, 7, null).andExpect(status().isNoContent());

        release(sellerId, "소명 접수").andExpect(status().isNoContent());
        saveMemo(sellerId, "CS 1024").andExpect(status().isNoContent());

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1).getAction()).isEqualTo(AdminAuditAction.SELLER_SUSPENSION_RELEASED);
        assertThat(rows.get(1).getReason()).isEqualTo("소명 접수");
        assertThat(rows.get(1).getTargetLabel()).isEqualTo("감사상회");
        assertThat(rows.get(2).getAction()).isEqualTo(AdminAuditAction.SELLER_MEMO_UPDATED);
        assertThat(rows.get(2).getTargetLabel()).isEqualTo("감사상회");
    }

    /** 거부당한 시도도 대상과 사유가 채워진 채 남아야 한다 — "하려 했지만 막혔다"가 요점인 조치다. */
    @Test
    void 거부된_정지_시도는_대상이_채워진_실패_행을_남긴다() throws Exception {
        long sellerId = seedSeller("감사상회");
        setStatus(sellerId, SellerStatus.WITHDRAWN);

        suspend(sellerId, "ABNORMAL_TRANSACTION", "정산 이상", 7, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(10009));

        AdminAuditLog row = onlyAuditRow();
        assertThat(row.getTargetId()).isEqualTo(String.valueOf(sellerId));
        assertThat(row.getTargetLabel()).isEqualTo("감사상회");
        assertThat(row.getReason()).isEqualTo("결제·정산 이상 거래 — 정산 이상");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.FAILURE);
        assertThat(row.getHttpStatus()).isEqualTo(409);
    }

    @Test
    void 셀러_조회는_감사_행을_남기지_않는다() throws Exception {
        long sellerId = seedSeller("조회상회");

        mockMvc.perform(bearer(get(BASE), actorToken)).andExpect(status().isOk());
        mockMvc.perform(bearer(get(BASE + "/" + sellerId), actorToken)).andExpect(status().isOk());

        assertThat(auditRows()).isEmpty();
    }

    // --- 보조 ---

    private ResultActions suspend(
            long sellerId, String reasonCode, String reasonDetail, Integer durationDays, String memo) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + sellerId + "/suspensions"), actorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(suspendBody(reasonCode, reasonDetail, durationDays, memo)));
    }

    private ResultActions release(long sellerId, String reasonDetail) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + sellerId + "/suspensions/release"), actorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonDetail\":" + json(reasonDetail) + ",\"memo\":null}"));
    }

    private ResultActions saveMemo(long sellerId, String memo) throws Exception {
        return mockMvc.perform(bearer(put(BASE + "/" + sellerId + "/memo"), actorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\":" + json(memo) + "}"));
    }

    private static String suspendBody(String reasonCode, String reasonDetail, Integer durationDays, String memo) {
        return "{\"reasonCode\":" + json(reasonCode) + ",\"reasonDetail\":" + json(reasonDetail)
                + ",\"durationDays\":" + (durationDays == null ? "null" : durationDays)
                + ",\"memo\":" + json(memo) + "}";
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private long seedSeller(String storeName) {
        long userId = createUser();
        tx.executeWithoutResult(txStatus -> em.persist(Seller.Companion.open(userId, storeName, 0L)));
        return userId;
    }

    /** 승인된 신청서 1건 — 사업자 유형과 대표자명의 출처다. */
    private void seedApplication(long sellerUserId, BusinessType businessType, String representativeName) {
        tx.executeWithoutResult(txStatus -> {
            SellerApplication application = SellerApplication.Companion.draft(sellerUserId, businessType);
            application.updateDraft(
                    businessType, "애착상회", "1208147521", null, representativeName, null, null, null, null);
            em.persist(application);
        });
    }

    private void seedProducts(long sellerId, int count) {
        tx.executeWithoutResult(txStatus -> {
            Category root = Category.Companion.create(null, Category.ROOT_DEPTH, "강아지", null, 1);
            em.persist(root);
            Category mid = Category.Companion.create(root, Category.MID_DEPTH, "사료", null, 1);
            em.persist(mid);
            Category leaf = Category.Companion.create(mid, Category.LEAF_DEPTH, "건사료", null, 1);
            em.persist(leaf);
            for (int i = 0; i < count; i++) {
                em.persist(Product.Companion.register(
                        leaf, sellerId, "상품" + i, null, null, 10_000L, null, null, null, List.of(), List.of()));
            }
        });
    }

    private void seedPendingSettlement(long sellerId) {
        tx.executeWithoutResult(txStatus -> em.persist(Settlement.Companion.create(
                sellerId,
                1L,
                sellerId,
                10_000L,
                0L,
                500L,
                200L,
                0L,
                new BigDecimal("0.0500"),
                new BigDecimal("0.0200"),
                LocalDate.now(),
                "settlement-" + sellerId)));
    }

    private void setConfirmedOrderCount(long sellerId, int count) {
        tx.executeWithoutResult(
                txStatus -> em.createQuery("update Seller s set s.confirmedOrderCount = :count where s.userId = :id")
                        .setParameter("count", count)
                        .setParameter("id", sellerId)
                        .executeUpdate());
    }

    /** 전이 메서드로 만들 수 없는 상태(탈퇴 등)를 심는다 — 셀러에는 아직 그 전이 경로가 없다. */
    private void setStatus(long sellerId, SellerStatus status) {
        tx.executeWithoutResult(txStatus -> em.createQuery("update Seller s set s.status = :st where s.userId = :id")
                .setParameter("st", status)
                .setParameter("id", sellerId)
                .executeUpdate());
    }

    private SellerStatus statusOf(long sellerId) {
        return tx.execute(
                txStatus -> em.createQuery("select s.status from Seller s where s.userId = :id", SellerStatus.class)
                        .setParameter("id", sellerId)
                        .getSingleResult());
    }

    /** 저장 검증은 별도 트랜잭션 재조회로 한다 — 1차 캐시가 남은 상태로 보면 가짜로 통과한다. */
    private Seller sellerOf(long sellerId) {
        return tx.execute(txStatus -> em.createQuery("select s from Seller s where s.userId = :id", Seller.class)
                .setParameter("id", sellerId)
                .getSingleResult());
    }

    /** 최신순 — 첫 원소가 가장 최근 조치다. */
    private List<SellerSanction> sanctionsOf(long sellerId) {
        return tx.execute(txStatus -> em.createQuery(
                        "select s from SellerSanction s where s.sellerId = :id order by s.id desc",
                        SellerSanction.class)
                .setParameter("id", sellerId)
                .getResultList());
    }

    private AdminAuditLog onlyAuditRow() {
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
