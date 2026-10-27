package com.aechak.admin.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.AdminClaimIntegrationTestBase;
import com.aechak.domain.order.claim.enums.ClaimFaultDecision;
import com.aechak.domain.order.claim.enums.ClaimInterventionReason;
import com.aechak.domain.order.claim.enums.ClaimType;
import com.aechak.domain.order.claim.enums.ReturnCostBearer;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 어드민 클레임 목록·상세 조회. 운영자는 양쪽 주장을 보고 판정하므로, 응답이 구매자측과 셀러측을
 * 모두 들고 있는지와 작업 순서(오래된 분쟁 먼저)가 맞는지를 본다.
 */
class AdminClaimQueryIntegrationTest extends AdminClaimIntegrationTestBase {

    @Test
    void 운영자가_아니면_목록을_볼_수_없다() throws Exception {
        String generalToken = mintAccessToken(createUser(UserStatus.ACTIVE, UserRole.GENERAL), UserRole.GENERAL);

        mockMvc.perform(bearer(get(BASE), generalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));

        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void 목록은_전_셀러_클레임을_분쟁이_오래된_순으로_내린다() throws Exception {
        var first = seedClaim(ClaimType.CANCEL, 11L, "먼저상회", seedBuyer("먼저구매자"));
        var second = seedClaim(ClaimType.RETURN, 22L, "나중상회", seedBuyer("나중구매자"));

        mockMvc.perform(bearer(get(BASE), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].claimPublicId").value(first.claimPublicId()))
                .andExpect(jsonPath("$.data.items[1].claimPublicId").value(second.claimPublicId()))
                .andExpect(jsonPath("$.data.items[0].sellerName").value("먼저상회"))
                .andExpect(jsonPath("$.data.items[0].buyerNickname").value("먼저구매자"));

        mockMvc.perform(bearer(get(BASE + "?sort=LATEST_FIRST"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].claimPublicId").value(second.claimPublicId()));
    }

    @Test
    void 축을_주지_않으면_개입할_수_있는_건으로_좁히고_명시하면_전체를_본다() throws Exception {
        var open = seedClaim();
        var refunded = seedClaim();
        approveBySeller(refunded.claimPublicId(), null);
        markRefunded(refunded.claimPublicId());
        var withdrawn = seedClaim();
        withdrawByBuyer(withdrawn.claimPublicId());

        mockMvc.perform(bearer(get(BASE), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].claimPublicId").value(open.claimPublicId()));

        mockMvc.perform(bearer(get(BASE + "?openOnly=false"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3));

        // 상태를 명시한 요청은 종결된 이력을 보려는 것이다 — 작업 목록 좁히기가 그것을 가려선 안 된다
        mockMvc.perform(bearer(get(BASE + "?status=WITHDRAWN"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].claimPublicId").value(withdrawn.claimPublicId()));

        // 두 축을 함께 주면 AND로 묶인다
        mockMvc.perform(bearer(get(BASE + "?status=WITHDRAWN&openOnly=true"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    void 유형과_상태_필터는_축별로_걸린다() throws Exception {
        seedClaim(ClaimType.CANCEL);
        var returnClaim = seedClaim(ClaimType.RETURN);
        var rejected = seedClaim(ClaimType.EXCHANGE);
        rejectBySeller(rejected.claimPublicId(), "하자가 확인되지 않습니다");

        mockMvc.perform(bearer(get(BASE + "?claimType=RETURN"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].claimPublicId").value(returnClaim.claimPublicId()))
                .andExpect(jsonPath("$.data.items[0].claimType").value("RETURN"))
                // 문구는 서버 enum이 정본이다 — 코드만 내리면 어드민 FE가 코드-문구 표를 따로 들어야 한다
                .andExpect(jsonPath("$.data.items[0].reasonCode").value("DEFECTIVE"))
                .andExpect(jsonPath("$.data.items[0].reasonLabel").value("상품이 파손되었거나 불량이에요"));

        mockMvc.perform(bearer(get(BASE + "?status=REJECTED"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].claimPublicId").value(rejected.claimPublicId()))
                .andExpect(jsonPath("$.data.items[0].sellerApprovalStatus").value("REJECTED"));

        mockMvc.perform(bearer(get(BASE + "?claimType=RETURN&claimType=EXCHANGE&status=CLAIM_REQUESTED"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].claimPublicId").value(returnClaim.claimPublicId()));
    }

    @Test
    void 셀러명과_구매자_닉네임으로_검색한다() throws Exception {
        var target = seedClaim(ClaimType.CANCEL, 31L, "멍냥펫샵", seedBuyer("코코맘"));
        seedClaim(ClaimType.CANCEL, 32L, "야옹상점", seedBuyer("나비아빠"));

        mockMvc.perform(bearer(get(BASE + "?sellerKeyword=멍냥"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].claimPublicId").value(target.claimPublicId()));

        mockMvc.perform(bearer(get(BASE + "?buyerKeyword=코코"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].claimPublicId").value(target.claimPublicId()));

        mockMvc.perform(bearer(get(BASE + "?sellerKeyword=멍냥&buyerKeyword=나비"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 기간_필터는_요청_시각_반열림_구간으로_걸린다() throws Exception {
        var seeded = seedClaim();
        LocalDateTime requestedAt = readClaim(seeded.claimPublicId()).getRequestedAt();

        mockMvc.perform(bearer(get(BASE + "?requestedFrom=" + requestedAt.minusMinutes(1)), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));

        // 끝 경계는 배타적이다 — 포함이면 "오늘까지" 조회가 내일 0시 건을 끌어온다
        mockMvc.perform(bearer(get(BASE + "?requestedTo=" + requestedAt), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0));

        mockMvc.perform(bearer(get(BASE + "?requestedTo=" + requestedAt.plusSeconds(1)), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    void 시작이_끝보다_늦은_기간은_거절한다() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        mockMvc.perform(bearer(get(BASE + "?requestedFrom=" + now.plusDays(1) + "&requestedTo=" + now), adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 커서로_다음_페이지를_이어_읽고_겹치거나_건너뛰지_않는다() throws Exception {
        List<String> seeded = List.of(
                seedClaim().claimPublicId(),
                seedClaim().claimPublicId(),
                seedClaim().claimPublicId());

        String body = mockMvc.perform(bearer(get(BASE + "?size=2"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = JsonPath.read(body, "$.data.nextCursor");
        assertThat((List<?>) JsonPath.read(body, "$.data.items")).hasSize(2);

        mockMvc.perform(bearer(get(BASE + "?size=2&cursor=" + cursor), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].claimPublicId").value(seeded.get(2)));
    }

    @Test
    void 망가진_커서는_거절한다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "?cursor=not-a-cursor"), adminToken)).andExpect(status().isBadRequest());
    }

    @Test
    void 상세는_구매자_주장과_셀러_처리를_함께_내린다() throws Exception {
        var seeded = seedClaim(ClaimType.RETURN);
        rejectBySeller(seeded.claimPublicId(), "사용 흔적이 있어 반품이 어렵습니다");

        mockMvc.perform(bearer(get(BASE + "/" + seeded.claimPublicId()), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claim.claimPublicId").value(seeded.claimPublicId()))
                .andExpect(jsonPath("$.data.claim.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.claim.refundAmount").value(25_000))
                // 구매자측
                .andExpect(jsonPath("$.data.buyerStatement.reasonCode").value("DEFECTIVE"))
                .andExpect(jsonPath("$.data.buyerStatement.reasonLabel").value("상품이 파손되었거나 불량이에요"))
                .andExpect(jsonPath("$.data.buyerStatement.reasonDetail").value("받았는데 포장이 터져 있었어요"))
                .andExpect(jsonPath("$.data.buyerStatement.requestedAt").exists())
                // 셀러측
                .andExpect(
                        jsonPath("$.data.sellerHandling.sellerApprovalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.sellerHandling.rejectionReason").value("사용 흔적이 있어 반품이 어렵습니다"))
                // 품목·이력·개입
                .andExpect(jsonPath("$.data.items[0].productName").value(PRODUCT_NAME))
                .andExpect(jsonPath("$.data.items[0].refundAmount").value(25_000))
                .andExpect(jsonPath("$.data.history[0].event").value("REQUESTED"))
                .andExpect(jsonPath("$.data.history[1].event").value("REJECTED"))
                .andExpect(jsonPath("$.data.intervention").doesNotExist())
                .andExpect(jsonPath("$.data.order.recipientName").value("홍길동"))
                // 응답 조립이 위치 인자라 같은 타입이 붙어 있는 자리가 뒤틀려도 컴파일은 통과한다 — 시각 세 칸을 못박는다
                .andExpect(jsonPath("$.data.claim.requestedAt").exists())
                .andExpect(jsonPath("$.data.claim.resolvedAt").exists())
                .andExpect(jsonPath("$.data.claim.approvedAt").doesNotExist())
                .andExpect(jsonPath("$.data.claim.sellerName").value("애착상회"))
                .andExpect(jsonPath("$.data.claim.buyerPublicId").exists());
    }

    @Test
    void 개입한_건은_상세와_목록에_판정이_함께_실린다() throws Exception {
        var seeded = seedClaim(ClaimType.RETURN);
        rejectBySeller(seeded.claimPublicId(), "확인되지 않습니다");
        intervene(seeded.claimPublicId());

        mockMvc.perform(bearer(get(BASE + "/" + seeded.claimPublicId()), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claim.status").value("ADMIN_OVERRIDDEN"))
                .andExpect(jsonPath("$.data.claim.faultDecision").value("SELLER_FAULT"))
                .andExpect(jsonPath("$.data.claim.interventionAllowed").value(false))
                .andExpect(jsonPath("$.data.intervention.faultDecision").value("SELLER_FAULT"))
                .andExpect(jsonPath("$.data.intervention.claimAccepted").value(true))
                .andExpect(jsonPath("$.data.intervention.reason").value("EVIDENCE_SUPPORTS_BUYER"))
                .andExpect(jsonPath("$.data.intervention.reasonLabel").value("구매자 주장에 근거가 있어요"))
                .andExpect(jsonPath("$.data.intervention.previousStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.intervention.previousSellerApprovalStatus")
                        .value("REJECTED"))
                .andExpect(jsonPath("$.data.intervention.previousResolvedAt").exists())
                .andExpect(jsonPath("$.data.intervention.returnCostBearer").value("SELLER"))
                // 셀러 거부와 운영자 개입이 이력에 둘 다 남아야 판정 근거가 된다
                .andExpect(jsonPath("$.data.history.length()").value(3))
                .andExpect(jsonPath("$.data.history[1].event").value("REJECTED"))
                .andExpect(jsonPath("$.data.history[2].event").value("ADMIN_OVERRIDDEN"));

        mockMvc.perform(bearer(get(BASE + "?status=ADMIN_OVERRIDDEN"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].faultDecision").value("SELLER_FAULT"))
                .andExpect(jsonPath("$.data.items[0].interventionAllowed").value(false));
    }

    @Test
    void 목록은_지금_개입할_수_있는_건을_가려_준다() throws Exception {
        var open = seedClaim();
        var refunded = seedClaim();
        approveBySeller(refunded.claimPublicId(), null);
        markRefunded(refunded.claimPublicId());
        var withdrawn = seedClaim();
        withdrawByBuyer(withdrawn.claimPublicId());

        String body = mockMvc.perform(bearer(get(BASE + "?openOnly=false"), adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(allowedOf(body, open.claimPublicId())).isTrue();
        assertThat(allowedOf(body, refunded.claimPublicId()))
                .as("환불이 끝난 건을 개입 가능으로 보이면 운영자가 돌려줄 돈을 두 번 센다")
                .isFalse();
        assertThat(allowedOf(body, withdrawn.claimPublicId())).isFalse();
    }

    @Test
    void 환불_예정액은_품목_환불액에_배송비_환불까지_더한다() throws Exception {
        // 주문 전부를 덮는 취소라 배송비까지 돌아간다 — 품목 합만 내리면 셀러 화면과 숫자가 갈린다
        var seeded = seedClaim(ClaimType.CANCEL, 41L, "애착상회", seedBuyer("배송비구매자"), 3_000L);

        mockMvc.perform(bearer(get(BASE), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].refundAmount").value(28_000))
                .andExpect(jsonPath("$.data.items[0].returnShippingFee").doesNotExist());

        mockMvc.perform(bearer(get(BASE + "/" + seeded.claimPublicId()), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claim.refundAmount").value(28_000))
                .andExpect(jsonPath("$.data.order.sellerShippingFee").value(3_000));
    }

    @Test
    void 작업_목록에서_빠지는_건도_상세는_열린다() throws Exception {
        var withdrawn = seedClaim();
        withdrawByBuyer(withdrawn.claimPublicId());

        // 목록 기본 좁히기가 상세까지 막으면 종결된 분쟁의 기록을 아무도 다시 볼 수 없다
        mockMvc.perform(bearer(get(BASE + "/" + withdrawn.claimPublicId()), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claim.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.claim.interventionAllowed").value(false));
    }

    @Test
    void 없는_클레임_상세는_404다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/01NOPE0000000000000000000"), adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(50400));
    }

    @Test
    void 개입_사유_어휘는_판정별로_내려온다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/intervention-reasons"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.faultDecisions.length()").value(3))
                .andExpect(jsonPath("$.data.faultDecisions[0].faultDecision").value("SELLER_FAULT"))
                .andExpect(jsonPath("$.data.faultDecisions[0].claimAccepted").value(true))
                .andExpect(jsonPath("$.data.faultDecisions[0].reasons[0].code").value("SELLER_UNRESPONSIVE"))
                .andExpect(jsonPath("$.data.faultDecisions[0].reasons[0].label").value("셀러가 기한 내 응답하지 않았어요"))
                .andExpect(jsonPath("$.data.faultDecisions[0].reasons[0].detailRequired")
                        .value(false))
                .andExpect(jsonPath("$.data.faultDecisions[2].faultDecision").value("CLAIM_DISMISSED"))
                .andExpect(jsonPath("$.data.faultDecisions[2].claimAccepted").value(false));
    }

    private boolean allowedOf(String body, String claimPublicId) {
        List<Boolean> found =
                JsonPath.read(body, "$.data.items[?(@.claimPublicId=='" + claimPublicId + "')].interventionAllowed");
        if (found.size() != 1) {
            throw new IllegalStateException("목록에서 찾지 못한 클레임: " + claimPublicId);
        }
        return found.getFirst();
    }

    private void intervene(String claimPublicId) {
        tx.executeWithoutResult(status -> {
            var claim = loadClaim(claimPublicId);
            em.persist(claim.overrideByAdmin(
                    7L,
                    ClaimFaultDecision.SELLER_FAULT,
                    ClaimInterventionReason.EVIDENCE_SUPPORTS_BUYER,
                    null,
                    ReturnCostBearer.SELLER,
                    null));
        });
    }
}
