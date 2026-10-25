package com.aechak.admin.inspection;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.inspection.AiCallLedgerEntry;
import com.aechak.domain.inspection.ContentInspection;
import com.aechak.domain.inspection.InspectionFinding;
import com.aechak.domain.inspection.ProductReviewQueueItem;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import com.aechak.domain.inspection.enums.PerspectiveOutcome;
import com.aechak.domain.inspection.repository.AiCallLedgerRepository;
import com.aechak.domain.product.category.Category;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.seller.seller.Seller;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 지표 테스트의 공용 픽스처 — <b>판정 수신 경로가 실제로 적는 모양</b>을 그대로 심는다.
 *
 * <p>검수 행만 만들고 관점 기록을 비워 두면 안 된다. 판정 적용은 관점마다 한 행씩 적고, 탈출
 * 분류가 그 행들로만 결정되기 때문이다 — 기록 없는 행은 어느 탈출로도 분류되지 않아 미분류
 * 경보를 만든다. 그러면 테스트가 실제로는 일어나지 않는 상태를 고정하게 된다.
 *
 * <p>탈출 버킷마다 전용 생성자를 둔다. 테스트가 "어떤 관점 행을 심을까"를 매번 다시 정하면
 * 같은 탈출을 두 테스트가 다른 모양으로 만들고, 그 차이가 분류 규칙의 버그를 가린다.
 */
abstract class InspectionMetricsTestBase extends IntegrationTestBase {

    /** 선별이 관점 호출을 건너뛸 때 그 관점 행에 적히는 사유. AI 레포와의 약속 문자열이다 */
    protected static final String TRIAGE_SKIP_REASON = "TRIAGE_CLEAR";

    /** 규칙이 적중해 선별에 묻지 않고 바로 승급했을 때 선별 행에 적히는 사유 */
    protected static final String RULE_HIT_SKIP_REASON = "RULE_HIT";

    /** 기한 정리가 미판정으로 닫을 때 남기는 표식. 백엔드가 적는 유일한 사유다 */
    protected static final String EXPIRED_SKIP_REASON = "WAIT_LIMIT_EXCEEDED";

    /** 스텁 모드 — 모델 호출이 한 번도 나가지 않은 건에 적히는 사유 */
    protected static final String STUB_SKIP_REASON = "LLM_DISABLED";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    protected AiCallLedgerRepository callLedger;

    protected MockMvc mockMvc;
    protected String adminToken;
    protected long sellerUserId;
    protected long productId;
    private long nextReviewRef;

    @BeforeEach
    void setUpMetricsFixture() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        adminToken = mintAccessToken(createUser());
        nextReviewRef = 1_000L;
        sellerUserId = createUser();
        tx.executeWithoutResult(txStatus -> {
            em.persist(Seller.Companion.open(sellerUserId, "애착상회", 0L));
            Category root = Category.Companion.create(null, Category.ROOT_DEPTH, "강아지", null, 1);
            em.persist(root);
            Category mid = Category.Companion.create(root, Category.MID_DEPTH, "사료", null, 1);
            em.persist(mid);
            Category leaf = Category.Companion.create(mid, Category.LEAF_DEPTH, "건사료", null, 1);
            em.persist(leaf);
            Product product = Product.Companion.register(
                    leaf,
                    sellerUserId,
                    "연어 건사료 2kg",
                    "우리 아이 주식으로 좋습니다.",
                    "products/thumbnail.png",
                    25_000L,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of());
            em.persist(product);
            em.flush();
            productId = product.getId();
        });
    }

    protected MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    // ---------------------------------------------------------------- 탈출별 픽스처

    /** 1단 — 규칙이 확정 차단했다. 파이프라인이 관점 호출 없이 조기 반환하므로 RULE 행 하나뿐이다 */
    protected long ruleBlocked(LocalDateTime settledAt) {
        long id = bareReview(InspectionJudgment.FLAG, settledAt);
        finding(id, InspectionPerspectiveKind.RULE, PerspectiveOutcome.VIOLATION, null);
        return id;
    }

    /** 2단 — 선별이 통과시켜 관점 호출을 건너뛰었다 */
    protected long triageCleared(LocalDateTime settledAt) {
        long id = bareReview(InspectionJudgment.PASS, settledAt);
        finding(id, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null);
        finding(id, InspectionPerspectiveKind.TRIAGE, PerspectiveOutcome.CLEAR, null);
        finding(id, InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.UNCHECKED, TRIAGE_SKIP_REASON);
        finding(id, InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.UNCHECKED, TRIAGE_SKIP_REASON);
        return id;
    }

    /** 3단 — 모델 관점이 보고 위반이 없다고 했다 */
    protected long modelClear(LocalDateTime settledAt) {
        return modelClear(settledAt, "policy-v1");
    }

    /** 정책 버전을 지정한 3단 탈출. 축 필터를 가르는 행을 만든다 */
    protected long modelClear(LocalDateTime settledAt, String policyVersion) {
        long id = bareReview(InspectionJudgment.PASS, settledAt, policyVersion);
        finding(id, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null);
        finding(id, InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.CLEAR, null);
        finding(id, InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.CLEAR, null);
        return id;
    }

    /**
     * 4단 — 비평이 모델 위반을 뒤집었다.
     *
     * <p>판정은 통과인데 모델 위반 관점 행이 남는다. 파이프라인이 위반 목록만 비우고 관점 행은
     * 되돌리지 않기 때문이고, 통과와 모델 위반이 공존하는 길은 뒤집힘뿐이다.
     */
    protected long criticOverturned(LocalDateTime settledAt) {
        long id = bareReview(InspectionJudgment.PASS, settledAt);
        finding(id, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null);
        finding(id, InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.VIOLATION, null);
        finding(id, InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.CLEAR, null);
        return id;
    }

    /** 5단 — 모델 위반이 확정됐다 */
    protected long modelFlag(LocalDateTime settledAt) {
        long id = bareReview(InspectionJudgment.FLAG, settledAt);
        finding(id, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null);
        finding(id, InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.VIOLATION, null);
        finding(id, InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.CLEAR, null);
        return id;
    }

    /** 판정 실패 — 관점 호출이 전부 죽어 쏜 관점이 0건이다 */
    protected long verdictFailed(LocalDateTime settledAt) {
        long id = bareReview(InspectionJudgment.FAILED, settledAt);
        finding(id, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null);
        finding(id, InspectionPerspectiveKind.ALL, PerspectiveOutcome.UNCHECKED, "ERROR");
        return id;
    }

    /** 미판정 — 기한 정리가 미검사 표식 하나만 적고 닫는다. 관점 폭은 복원되지 않는다 */
    protected long unjudged(LocalDateTime settledAt) {
        long id = bareReview(InspectionJudgment.UNJUDGED, settledAt);
        finding(id, InspectionPerspectiveKind.ALL, PerspectiveOutcome.UNCHECKED, EXPIRED_SKIP_REASON);
        return id;
    }

    /** 레이트 리밋으로 모델 관점이 보지 못한 승급 건. 검출율 분모에는 들어오지 않아야 한다 */
    protected long modelUnchecked(LocalDateTime settledAt) {
        long id = bareReview(InspectionJudgment.FAILED, settledAt);
        finding(id, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null);
        finding(id, InspectionPerspectiveKind.TRIAGE, PerspectiveOutcome.UNCHECKED, "PROMOTED");
        finding(id, InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.UNCHECKED, "RATE_LIMITED");
        finding(id, InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.UNCHECKED, "RATE_LIMITED");
        return id;
    }

    /** 스텁 모드 — 모델 호출이 한 번도 나가지 않았다. 3단 분모에 들어오면 안 된다 */
    protected long stubbed(LocalDateTime settledAt) {
        long id = bareReview(InspectionJudgment.FAILED, settledAt);
        finding(id, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null);
        finding(id, InspectionPerspectiveKind.ALL, PerspectiveOutcome.UNCHECKED, STUB_SKIP_REASON);
        return id;
    }

    // ---------------------------------------------------------------- 저수준 픽스처

    /** 관점 기록 없는 검수 한 건. 미분류 경보를 일부러 만들 때만 직접 쓴다 */
    protected long bareReview(InspectionJudgment judgment, LocalDateTime settledAt) {
        return bareReview(judgment, settledAt, "policy-v1");
    }

    protected long bareReview(InspectionJudgment judgment, LocalDateTime settledAt, String policyVersion) {
        long targetRef = nextReviewRef++;
        return settle(
                InspectionTargetType.REVIEW,
                targetRef,
                "r%063d".formatted(targetRef),
                judgment,
                settledAt,
                null,
                policyVersion);
    }

    /** 상품 대상의 검수. 대상 종류 축을 가르고 적체의 최신 판정을 만든다 */
    protected long settledProduct(String contentHash, InspectionJudgment judgment, LocalDateTime settledAt) {
        return settle(InspectionTargetType.PRODUCT, productId, contentHash, judgment, settledAt, 1, "policy-v1");
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private long settle(
            InspectionTargetType targetType,
            long targetRef,
            String contentHash,
            InspectionJudgment judgment,
            LocalDateTime settledAt,
            Integer versionNo,
            String policyVersion) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            ContentInspection inspection =
                    ContentInspection.Companion.queue(targetType, targetRef, contentHash, policyVersion, versionNo);
            em.persist(inspection);
            inspection.settle(judgment, "inspection-v1+" + policyVersion + "+triage-v2", null, null, null, settledAt);
            em.flush();
            return inspection.getId();
        }));
    }

    protected void finding(
            long inspectionId, InspectionPerspectiveKind perspective, PerspectiveOutcome outcome, String skipReason) {
        tx.executeWithoutResult(txStatus ->
                em.persist(InspectionFinding.Companion.of(inspectionId, perspective, outcome, skipReason, null)));
    }

    /** 관리자 검토 큐에 행을 연다. 판정 적용이 적는 것과 같은 행이다 */
    protected long openQueueRow(LocalDateTime blindedAt) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            ProductReviewQueueItem item =
                    ProductReviewQueueItem.Companion.openForProduct(productId, sellerUserId, "연어 건사료 2kg", blindedAt);
            em.persist(item);
            em.flush();
            return item.getId();
        }));
    }

    /** 관리자가 결정해 큐 행을 닫는다. 닫힌 행은 적체에 들어오지 않아야 한다 */
    protected void approveQueueRow(long itemId, LocalDateTime now) {
        tx.executeWithoutResult(
                txStatus -> em.find(ProductReviewQueueItem.class, itemId).approve(now));
    }

    /** 원장 적재는 항상 현재 시각을 적는다. 창 경계를 만들려면 시각만 뒤로 민다 */
    protected void backdateLedger(long inspectionId, LocalDateTime createdAt) {
        tx.executeWithoutResult(
                txStatus -> em.createNativeQuery("update ai_call_ledger set created_at = :at where inspection_id = :id")
                        .setParameter("at", createdAt)
                        .setParameter("id", inspectionId)
                        .executeUpdate());
    }

    protected void triageCall(long inspectionId, String outcome, long costMicroUsd) {
        // 선별 프롬프트는 Haiku 최소 캐시 길이에 못 미쳐 캐시를 쓰지 못한다 — 캐시 읽기가 0이다.
        ledger(inspectionId, InspectionPerspectiveKind.TRIAGE.name(), outcome, costMicroUsd, 0, 900);
    }

    protected void modelCall(long inspectionId, InspectionPerspectiveKind perspective, long costMicroUsd) {
        ledger(inspectionId, perspective.name(), "SUCCESS", costMicroUsd, 1_200, 2_500);
    }

    protected void ledger(
            long inspectionId, String perspective, String outcome, long costMicroUsd, int cacheRead, int latencyMs) {
        callLedger.insertIgnoringDuplicates(List.of(AiCallLedgerEntry.Companion.of(
                "req-" + inspectionId,
                "call-" + inspectionId + "-" + perspective + "-" + outcome + "-" + latencyMs,
                inspectionId,
                "REVIEW_INSPECTION",
                perspective,
                "claude-haiku-4-5-20251001",
                "inspection-v1+policy-v1",
                300,
                cacheRead,
                0,
                100,
                costMicroUsd,
                latencyMs,
                outcome)));
    }
}
