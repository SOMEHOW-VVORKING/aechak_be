package com.aechak.admin.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.AdminClaimIntegrationTestBase;
import com.aechak.domain.order.claim.Claim;
import com.aechak.domain.order.claim.ClaimItem;
import com.aechak.domain.order.claim.enums.ClaimFaultDecision;
import com.aechak.domain.order.claim.enums.ClaimInterventionReason;
import com.aechak.domain.order.claim.enums.ClaimReasonCode;
import com.aechak.domain.order.claim.enums.ClaimStatus;
import com.aechak.domain.order.claim.enums.ClaimType;
import com.aechak.domain.order.claim.enums.RefundStatus;
import com.aechak.domain.order.claim.enums.ReturnCostBearer;
import com.aechak.domain.order.claim.enums.SellerApprovalStatus;
import com.aechak.domain.order.order.Order;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 운영자 개입. 응답만 보고 끝내지 않고 저장된 클레임과 개입 기록까지 읽어, 부작용이 실제로 일어났는지와
 * 거절된 요청이 아무것도 남기지 않았는지를 함께 본다.
 */
class AdminClaimInterventionIntegrationTest extends AdminClaimIntegrationTestBase {

    @Test
    void 운영자가_아니면_개입할_수_없고_클레임도_그대로다() throws Exception {
        var seeded = seedClaim();
        rejectBySeller(seeded.claimPublicId(), "확인되지 않습니다");
        String generalToken = mintAccessToken(createUser(UserStatus.ACTIVE, UserRole.GENERAL), UserRole.GENERAL);

        mockMvc.perform(intervention(seeded.claimPublicId(), sellerFaultBody(), generalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));

        mockMvc.perform(post(BASE + "/" + seeded.claimPublicId() + "/intervention")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sellerFaultBody()))
                .andExpect(status().isUnauthorized());

        Claim claim = readClaim(seeded.claimPublicId());
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(countInterventions(seeded.claimId())).isZero();
    }

    @Test
    void 셀러_거부를_뒤집으면_개입_완료로_종결되고_기록이_남는다() throws Exception {
        var seeded = seedClaim(ClaimType.RETURN);
        rejectBySeller(seeded.claimPublicId(), "사용 흔적이 있습니다");
        LocalDateTime sellerResolvedAt = readClaim(seeded.claimPublicId()).getResolvedAt();

        mockMvc.perform(intervention(seeded.claimPublicId(), sellerFaultBody(), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ADMIN_OVERRIDDEN"))
                .andExpect(jsonPath("$.data.returnCostBearer").value("SELLER"))
                .andExpect(jsonPath("$.data.refundStatus").value("NONE"))
                .andExpect(jsonPath("$.data.refundSettled").value(false))
                .andExpect(jsonPath("$.data.intervention.faultDecision").value("SELLER_FAULT"))
                .andExpect(jsonPath("$.data.intervention.reason").value("EVIDENCE_SUPPORTS_BUYER"))
                .andExpect(jsonPath("$.data.intervention.reasonDetail").value("사진으로 파손이 확인됨"))
                .andExpect(jsonPath("$.data.intervention.internalMemo").value("재발 시 셀러 경고"))
                .andExpect(jsonPath("$.data.intervention.previousStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.intervention.previousSellerApprovalStatus")
                        .value("REJECTED"));

        Claim claim = readClaim(seeded.claimPublicId());
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ADMIN_OVERRIDDEN);
        assertThat(claim.getAdminReason()).isEqualTo("사진으로 파손이 확인됨");
        assertThat(claim.getReturnCostBearer()).isEqualTo(ReturnCostBearer.SELLER);
        assertThat(claim.getResolvedAt()).isAfterOrEqualTo(sellerResolvedAt);
        assertThat(claim.getRejectionReason())
                .as("셀러가 쓴 거부 사유를 지우면 무엇을 뒤집었는지 알 수 없다")
                .isEqualTo("사용 흔적이 있습니다");
        assertThat(claim.getSellerApprovalStatus())
                .as("셀러 판단 축은 개입이 건드리지 않는다 — 덮은 흔적은 개입 기록이 든다")
                .isEqualTo(SellerApprovalStatus.REJECTED);
        assertThat(countInterventions(seeded.claimId())).isEqualTo(1);
    }

    @Test
    void 개입은_환불_축을_건드리지_않는다() throws Exception {
        var seeded = seedClaim();
        rejectBySeller(seeded.claimPublicId(), "확인되지 않습니다");

        mockMvc.perform(intervention(seeded.claimPublicId(), sellerFaultBody(), adminToken))
                .andExpect(status().isOk());

        Claim claim = readClaim(seeded.claimPublicId());
        assertThat(claim.getRefundStatus()).as("개입이 환불 축을 움직이면 집행 경로가 두 벌이 된다").isEqualTo(RefundStatus.NONE);
        assertThat(claim.isRefundExecutable())
                .as("개입 종결 건이 환불 스캔에 걸리면 판정만으로 돈이 나간다")
                .isFalse();
    }

    @Test
    void 환불이_움직인_건은_개입을_거절하고_아무것도_바꾸지_않는다() throws Exception {
        var refunded = seedClaim();
        approveBySeller(refunded.claimPublicId(), null);
        markRefunded(refunded.claimPublicId());
        var inFlight = seedClaim();
        approveBySeller(inFlight.claimPublicId(), null);
        markRefundRequested(inFlight.claimPublicId());
        var failed = seedClaim();
        approveBySeller(failed.claimPublicId(), null);
        markRefundFailed(failed.claimPublicId());

        for (var seeded : List.of(refunded, inFlight, failed)) {
            mockMvc.perform(intervention(seeded.claimPublicId(), sellerFaultBody(), adminToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value(50414));
            assertThat(readClaim(seeded.claimPublicId()).getStatus()).isNotEqualTo(ClaimStatus.ADMIN_OVERRIDDEN);
            assertThat(countInterventions(seeded.claimId())).isZero();
        }
        assertThat(readClaim(refunded.claimPublicId()).getRefundStatus()).isEqualTo(RefundStatus.DONE);
        assertThat(readClaim(inFlight.claimPublicId()).getRefundStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(readClaim(failed.claimPublicId()).getStatus()).isEqualTo(ClaimStatus.REFUND_FAILED);
    }

    @Test
    void 철회된_건과_이미_개입한_건은_다시_개입할_수_없다() throws Exception {
        var withdrawn = seedClaim();
        withdrawByBuyer(withdrawn.claimPublicId());
        var twice = seedClaim();
        mockMvc.perform(intervention(twice.claimPublicId(), sellerFaultBody(), adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(intervention(withdrawn.claimPublicId(), sellerFaultBody(), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(50414));
        mockMvc.perform(intervention(twice.claimPublicId(), sellerFaultBody(), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(50414));

        assertThat(countInterventions(withdrawn.claimId())).isZero();
        assertThat(countInterventions(twice.claimId()))
                .as("두 번째 개입이 기록을 하나 더 쌓으면 어느 판정이 유효한지 알 수 없다")
                .isEqualTo(1);
    }

    /**
     * 겹친 두 개입은 둘 다 같은 클레임 상태를 읽어 상태 판정을 통과한다. 기록을 먼저 심고 클레임 행은
     * 그대로 둬 그 순간을 그대로 만든다 — 늦은 쪽이 기록 UNIQUE에 걸릴 때 500이 아니라 409가 나가야 한다.
     */
    @Test
    void 겹친_개입은_기록_UNIQUE가_막고_409로_나간다() throws Exception {
        var seeded = seedClaim(ClaimType.RETURN);
        rejectBySeller(seeded.claimPublicId(), "확인되지 않습니다");
        seedCompetingInterventionRecord(seeded.claimPublicId());

        mockMvc.perform(intervention(seeded.claimPublicId(), sellerFaultBody(), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(50414));

        Claim claim = readClaim(seeded.claimPublicId());
        assertThat(claim.getStatus()).as("기록이 막힌 요청의 클레임 변경까지 함께 되돌아가야 한다").isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.getAdminReason()).isNull();
        assertThat(countInterventions(seeded.claimId())).isEqualTo(1);
    }

    /** 클레임 행은 건드리지 않고 개입 기록 1행만 심는다 — 분리한 복사본으로 뒤집어 그 기록만 꺼낸다. */
    private void seedCompetingInterventionRecord(String claimPublicId) {
        tx.executeWithoutResult(status -> {
            Claim detached = loadClaim(claimPublicId);
            em.detach(detached);
            em.persist(detached.overrideByAdmin(
                    9_999L,
                    ClaimFaultDecision.SELLER_FAULT,
                    ClaimInterventionReason.SELLER_UNRESPONSIVE,
                    null,
                    null,
                    null));
            em.flush();
        });
    }

    @Test
    void 뒤집으려는_품목을_다른_클레임이_이미_붙잡고_있으면_거절한다() throws Exception {
        var seeded = seedClaim(ClaimType.RETURN);
        rejectBySeller(seeded.claimPublicId(), "확인되지 않습니다");
        // 거부로 품목이 풀려 구매자가 같은 품목에 새 클레임을 걸 수 있다
        long followUpId = seedFollowUpClaim(seeded.orderId());

        mockMvc.perform(intervention(seeded.claimPublicId(), sellerFaultBody(), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(50403));

        assertThat(readClaim(seeded.claimPublicId()).getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(countInterventions(seeded.claimId())).isZero();
        assertThat(followUpId).isPositive();
    }

    @Test
    void 재발송으로_종결된_교환은_개입할_수_없고_종결_시각도_그대로다() throws Exception {
        var seeded = seedClaim(ClaimType.EXCHANGE);
        completeExchange(seeded.claimPublicId());
        LocalDateTime reshippedAt = readClaim(seeded.claimPublicId()).getResolvedAt();

        mockMvc.perform(intervention(seeded.claimPublicId(), sellerFaultBody(), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(50414));

        Claim claim = readClaim(seeded.claimPublicId());
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getResolvedAt())
                .as("개입이 종결 시각을 덮으면 재발송이 언제 끝났는지가 사라진다")
                .isEqualTo(reshippedAt);
        assertThat(claim.getReturnCostBearer())
                .as("셀러가 승인하며 정한 부담 주체를 끝난 건에서 바꾸면 정산 근거가 흔들린다")
                .isEqualTo(ReturnCostBearer.SELLER);
        assertThat(countInterventions(seeded.claimId())).isZero();
    }

    @Test
    void 취소_클레임은_부담_주체를_명시해도_비워_둔다() throws Exception {
        var seeded = seedClaim(ClaimType.CANCEL);

        mockMvc.perform(intervention(
                        seeded.claimPublicId(),
                        """
                        {"faultDecision":"BUYER_FAULT","reason":"BUYER_CHANGE_OF_MIND","returnCostBearer":"BUYER"}
                        """,
                        adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.returnCostBearer").doesNotExist());

        assertThat(readClaim(seeded.claimPublicId()).getReturnCostBearer())
                .as("발송 전 취소에는 반품 발송비가 없다 — 셀러 승인 경로와 같은 규칙")
                .isNull();
    }

    @Test
    void 클레임_불가_판정은_부담_주체를_명시해도_남기지_않는다() throws Exception {
        var seeded = seedClaim(ClaimType.RETURN);

        // 부담 주체를 명시한다 — 생략한 입력만 보면 판정 기본값이 비어 있는 덕에 통과하고 덮어쓰기 구멍을 지나간다
        mockMvc.perform(intervention(
                        seeded.claimPublicId(),
                        """
                        {"faultDecision":"CLAIM_DISMISSED","reason":"BUYER_ABUSE","returnCostBearer":"BUYER"}
                        """,
                        adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intervention.claimAccepted").value(false))
                .andExpect(jsonPath("$.data.returnCostBearer").doesNotExist())
                .andExpect(jsonPath("$.data.intervention.returnCostBearer").doesNotExist());

        assertThat(readClaim(seeded.claimPublicId()).getReturnCostBearer())
                .as("성립하지 않은 클레임에 부담 주체가 남으면 정산 없는 반품비를 청구한다")
                .isNull();
    }

    @Test
    void 판정에서_고를_수_없는_사유는_거절한다() throws Exception {
        var seeded = seedClaim(ClaimType.RETURN);

        mockMvc.perform(intervention(
                        seeded.claimPublicId(),
                        """
                        {"faultDecision":"SELLER_FAULT","reason":"BUYER_ABUSE"}
                        """,
                        adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(50415));

        mockMvc.perform(intervention(
                        seeded.claimPublicId(),
                        """
                        {"faultDecision":"BUYER_FAULT","reason":"SELLER_UNRESPONSIVE"}
                        """,
                        adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(50415));

        assertThat(readClaim(seeded.claimPublicId()).getStatus()).isEqualTo(ClaimStatus.CLAIM_REQUESTED);
        assertThat(countInterventions(seeded.claimId())).isZero();
    }

    @Test
    void 기타_사유는_상세_없이_받지_않는다() throws Exception {
        var seeded = seedClaim(ClaimType.RETURN);

        mockMvc.perform(intervention(
                        seeded.claimPublicId(),
                        """
                        {"faultDecision":"SELLER_FAULT","reason":"OTHER","reasonDetail":"   "}
                        """,
                        adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(50416));

        assertThat(countInterventions(seeded.claimId())).isZero();
    }

    @Test
    void 판정과_사유는_필수고_사유_길이는_제한된다() throws Exception {
        var seeded = seedClaim();

        mockMvc.perform(intervention(seeded.claimPublicId(), "{}", adminToken)).andExpect(status().isBadRequest());

        mockMvc.perform(intervention(
                        seeded.claimPublicId(),
                        "{\"faultDecision\":\"SELLER_FAULT\",\"reason\":\"SELLER_UNRESPONSIVE\",\"reasonDetail\":\""
                                + "가".repeat(501) + "\"}",
                        adminToken))
                .andExpect(status().isBadRequest());

        assertThat(countInterventions(seeded.claimId())).isZero();
    }

    @Test
    void 없는_클레임에_개입하면_404다() throws Exception {
        mockMvc.perform(intervention("01NOPE0000000000000000000", sellerFaultBody(), adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(50400));
    }

    /** 거부로 풀린 품목에 같은 구매자가 다시 건 클레임. 운영자가 뒤집으면 한 품목을 두 클레임이 붙잡는다. */
    private long seedFollowUpClaim(long orderId) {
        return Objects.requireNonNull(tx.execute(status -> {
            Order order = em.find(Order.class, orderId);
            Claim followUp = Claim.Companion.request(
                    order,
                    ClaimType.RETURN,
                    ClaimReasonCode.WRONG_ITEM,
                    null,
                    order.getItems().stream()
                            .map(item -> ClaimItem.Companion.of(item, item.getQuantity(), 25_000L))
                            .toList(),
                    LocalDateTime.now().plusDays(2));
            em.persist(followUp);
            em.flush();
            return followUp.getId();
        }));
    }

    private MockHttpServletRequestBuilder intervention(String claimPublicId, String body, String token) {
        return bearer(
                post(BASE + "/" + claimPublicId + "/intervention")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                token);
    }

    private String sellerFaultBody() {
        return """
                {
                  "faultDecision": "SELLER_FAULT",
                  "reason": "EVIDENCE_SUPPORTS_BUYER",
                  "reasonDetail": "사진으로 파손이 확인됨",
                  "internalMemo": "재발 시 셀러 경고"
                }
                """;
    }
}
