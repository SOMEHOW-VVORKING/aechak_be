package com.aechak.admin.inspection;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.inspection.ContentInspection;
import com.aechak.domain.inspection.InspectionFinding;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import com.aechak.domain.inspection.enums.PerspectiveOutcome;
import com.aechak.domain.product.category.Category;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.seller.seller.Seller;
import com.aechak.domain.user.user.enums.UserRole;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * [통합] 검수 진행 현황 조회 — 권한 경계, 세 필터, 정렬, 페이지 보정, 상세를 HTTP로 검증한다.
 *
 * <p><b>가장 중요한 단언은 "미검사가 통과와 다르게 내려온다"다.</b> 그것이 깨지면 모델이 보지 못한
 * 관점이 화면에서 "문제 없음"으로 보이고, 운영자는 검수를 마친 상품으로 읽는다. 바로 다음이 판정의
 * UNJUDGED(기한 초과)·FAILED(처리 실패)가 PASS와 섞이지 않는지다 — 셋을 한 묶음으로 묶으면 같은 일이
 * 판정 축에서 벌어진다.
 *
 * <p>검수 행은 도메인으로 직접 심는다. 판정 수신·요청 발행 경로는 api 소유라 이 모듈에 없다.
 */
class AdminInspectionQueryIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/inspections";
    private static final String POLICY = "policy-v1";
    private static final String PROMPT = "inspection-v1+policy-v1+triage-v2";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;
    private String adminToken;
    private long productId;

    @BeforeEach
    void setUpMockMvcAndFixtures() {
        // 시큐리티 체인을 끼운다. 빼면 401·403 단언이 거짓으로 통과한다.
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        adminToken = mintAccessToken(createUser());
        long sellerUserId = createUser();
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

    // --- 권한 경계 ---

    @Test
    void 토큰_없이_목록을_조회하면_401_20004_를_반환한다() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(20004));
    }

    @Test
    void 일반_GENERAL_토큰은_목록_접근이_403_20011_로_막힌다() throws Exception {
        mockMvc.perform(bearer(get(BASE), mintAccessToken(createUser(), UserRole.GENERAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }

    @Test
    void 일반_GENERAL_토큰은_상세_접근도_403_20011_로_막힌다() throws Exception {
        long inspectionId = queued(productId, hash("a"));

        mockMvc.perform(bearer(get(BASE + "/" + inspectionId), mintAccessToken(createUser(), UserRole.GENERAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }

    // --- 목록: 정렬과 대상 이름 ---

    @Test
    void 목록은_요청_시각_내림차순으로_오고_상품명을_함께_준다() throws Exception {
        LocalDateTime base = LocalDateTime.now().minusHours(3);
        long oldest = queued(productId, hash("old"));
        backdateRequestedAt(oldest, base);
        long middle = queued(productId, hash("mid"));
        backdateRequestedAt(middle, base.plusMinutes(10));
        long newest = queued(productId, hash("new"));
        backdateRequestedAt(newest, base.plusMinutes(20));

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items[0].inspectionId").value(newest))
                .andExpect(jsonPath("$.data.items[1].inspectionId").value(middle))
                .andExpect(jsonPath("$.data.items[2].inspectionId").value(oldest))
                .andExpect(jsonPath("$.data.items[0].productName").value("연어 건사료 2kg"))
                .andExpect(jsonPath("$.data.items[0].targetType").value("PRODUCT"))
                .andExpect(jsonPath("$.data.items[0].targetRef").value(productId))
                .andExpect(jsonPath("$.data.items[0].policyVersion").value(POLICY));
    }

    /** 아직 판정을 못 받은 건은 판정이 비어서 온다. 그 칸을 PASS로 채우면 통과한 상품으로 읽힌다. */
    @Test
    void 판정을_받지_못한_건은_판정이_null_로_오고_통과로_채워지지_않는다() throws Exception {
        queued(productId, hash("q"));

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].processingState").value("QUEUED"))
                .andExpect(jsonPath("$.data.items[0].judgment").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].settledAt").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].attempt").value(0));
    }

    /**
     * 리뷰 검수에는 상품명이 붙지 않는다.
     *
     * <p>대상 식별자를 그대로 상품 기본키로 쓰는 조인이라 종류 조건을 빼면 같은 번호의 상품 이름이
     * 리뷰 행에 붙는다. 그래서 대상 참조를 상품 id와 <b>같은 값</b>으로 심어 그 자리를 찍는다.
     */
    @Test
    void 리뷰_검수는_상품명_없이_오고_상품_식별자도_비어_있다() throws Exception {
        queueOn(InspectionTargetType.REVIEW, productId, hash("rv"));

        mockMvc.perform(bearer(get(BASE).param("targetType", "REVIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].targetType").value("REVIEW"))
                .andExpect(jsonPath("$.data.items[0].targetRef").value(productId))
                .andExpect(jsonPath("$.data.items[0].productId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].productName").doesNotExist());
    }

    // --- 목록: 필터 ---

    @Test
    void 처리_상태_필터는_그_상태의_건만_준다() throws Exception {
        queued(productId, hash("s1"));
        long leased = leased(productId, hash("s2"), "worker-1");
        settled(productId, hash("s3"), InspectionJudgment.PASS);

        mockMvc.perform(bearer(get(BASE).param("processingState", "IN_FLIGHT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].inspectionId").value(leased))
                .andExpect(jsonPath("$.data.items[0].processingState").value("IN_FLIGHT"))
                .andExpect(jsonPath("$.data.items[0].attempt").value(1));
    }

    @Test
    void 대상_종류_필터는_상품_검수만_준다() throws Exception {
        long product = queued(productId, hash("t1"));
        queueOn(InspectionTargetType.REVIEW, 9_001L, hash("t2"));

        mockMvc.perform(bearer(get(BASE).param("targetType", "PRODUCT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].inspectionId").value(product));
    }

    /**
     * 판정 필터는 통과만 준다.
     *
     * <p>기한 초과(UNJUDGED)와 처리 실패(FAILED)는 통과가 아니다. 셋 중 둘이 섞여 오면 운영자가
     * 검수를 마친 건으로 읽는다 — 관점 결과의 미검사와 같은 종류의 사고다.
     */
    @Test
    void 판정_필터는_통과만_주고_기한초과와_실패를_섞지_않는다() throws Exception {
        long passed = settled(productId, hash("j1"), InspectionJudgment.PASS);
        settled(productId, hash("j2"), InspectionJudgment.UNJUDGED);
        settled(productId, hash("j3"), InspectionJudgment.FAILED);
        settled(productId, hash("j4"), InspectionJudgment.FLAG);

        mockMvc.perform(bearer(get(BASE).param("judgment", "PASS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].inspectionId").value(passed))
                .andExpect(jsonPath("$.data.items[0].judgment").value("PASS"));
    }

    @Test
    void 필터를_주지_않으면_처리_상태와_판정에_상관없이_전부_온다() throws Exception {
        queued(productId, hash("a1"));
        leased(productId, hash("a2"), "worker-1");
        settled(productId, hash("a3"), InspectionJudgment.PASS);
        settled(productId, hash("a4"), InspectionJudgment.UNJUDGED);
        queueOn(InspectionTargetType.REVIEW, 9_002L, hash("a5"));

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(5))
                .andExpect(jsonPath("$.data.items.length()").value(5));
    }

    @Test
    void 알_수_없는_필터_값은_400_90001_로_거절한다() throws Exception {
        mockMvc.perform(bearer(get(BASE).param("processingState", "RUNNING")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));
    }

    // --- 목록: 페이지 보정 ---

    @Test
    void 음수_page_와_0_size_는_첫_페이지_한_건으로_보정된다() throws Exception {
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        long older = queued(productId, hash("p1"));
        backdateRequestedAt(older, base);
        long newer = queued(productId, hash("p2"));
        backdateRequestedAt(newer, base.plusMinutes(5));

        mockMvc.perform(bearer(get(BASE).param("page", "-3").param("size", "0")))
                .andExpect(status().isOk())
                // 전체 건수는 필터 기준이라 페이지 크기에 깎이지 않는다.
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].inspectionId").value(newer));
    }

    @Test
    void size_가_상한을_넘으면_100_으로_절삭된다() throws Exception {
        queued(productId, hash("p3"));

        mockMvc.perform(bearer(get(BASE).param("size", "5000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void 범위를_넘은_page_는_빈_목록을_주고_전체_건수는_남는다() throws Exception {
        queued(productId, hash("p4"));

        mockMvc.perform(bearer(get(BASE).param("page", "7").param("size", "20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    // --- 상세 ---

    /**
     * <b>이 수트에서 가장 중요한 단언.</b> 미검사(UNCHECKED)는 통과(CLEAR)와 다른 값으로 내려오고
     * 그 이유가 함께 온다.
     *
     * <p>판정이 통과인 건에도 미검사 관점이 남는다 — 선별이 통과시키면 모델 관점 호출이 나가지 않고
     * 그 관점 행은 "보지 못했다"로 적힌다. 미검사를 통과로 그리면 운영자는 네 관점을 다 본 상품으로
     * 읽는다.
     */
    @Test
    void 상세의_관점_결과는_미검사를_통과와_다르게_주고_사유를_함께_준다() throws Exception {
        long inspectionId = settled(productId, hash("d1"), InspectionJudgment.PASS);
        finding(inspectionId, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null, null);
        finding(inspectionId, InspectionPerspectiveKind.TRIAGE, PerspectiveOutcome.CLEAR, null, null);
        finding(
                inspectionId,
                InspectionPerspectiveKind.DISPLAY_ADS,
                PerspectiveOutcome.UNCHECKED,
                "TRIAGE_CLEAR",
                null);
        finding(
                inspectionId,
                InspectionPerspectiveKind.CONDUCT_TRADE,
                PerspectiveOutcome.VIOLATION,
                null,
                "[{\"code\":\"DIRECT_TRADE\"}]");

        mockMvc.perform(bearer(get(BASE + "/" + inspectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment").value("PASS"))
                .andExpect(jsonPath("$.data.perspectives.length()").value(4))
                // 파이프라인 순서로 온다 — 열거 선언 순서도, 저장 순서도 아니다.
                .andExpect(jsonPath("$.data.perspectives[0].perspective").value("RULE"))
                .andExpect(jsonPath("$.data.perspectives[0].outcome").value("CLEAR"))
                .andExpect(jsonPath("$.data.perspectives[0].skipReason").doesNotExist())
                .andExpect(jsonPath("$.data.perspectives[1].perspective").value("TRIAGE"))
                .andExpect(jsonPath("$.data.perspectives[1].outcome").value("CLEAR"))
                // 통과가 아니라 미검사다. 사유가 함께 와야 운영자가 왜 안 봤는지 안다.
                .andExpect(jsonPath("$.data.perspectives[2].perspective").value("DISPLAY_ADS"))
                .andExpect(jsonPath("$.data.perspectives[2].outcome").value("UNCHECKED"))
                .andExpect(jsonPath("$.data.perspectives[2].skipReason").value("TRIAGE_CLEAR"))
                .andExpect(jsonPath("$.data.perspectives[3].perspective").value("CONDUCT_TRADE"))
                .andExpect(jsonPath("$.data.perspectives[3].outcome").value("VIOLATION"))
                // 위반 상세는 JSON 칼럼에 적힌 원문이 그대로 온다. MySQL이 저장 시점에 공백을 정규화하므로
                // 바이트가 같은지는 보지 않는다 — 분류 어휘가 살아 오는지만 본다(그 모양은 AI 레포가 쥔다).
                .andExpect(jsonPath("$.data.perspectives[3].violations", containsString("DIRECT_TRADE")))
                // 사유를 드는 칸은 미검사뿐이다. 통과·위반 칸에 사유가 생기면 두 값이 뭉개진 것이다.
                .andExpect(jsonPath("$.data.perspectives[1].skipReason").doesNotExist())
                .andExpect(jsonPath("$.data.perspectives[3].skipReason").doesNotExist());
    }

    /** 기한 정리로 닫힌 건은 미판정으로 남고 관점 폭도 복원되지 않는다. 통과로 보이면 안 된다. */
    @Test
    void 기한_초과로_닫힌_건은_미판정과_미검사로_온다() throws Exception {
        long inspectionId = settled(productId, hash("d2"), InspectionJudgment.UNJUDGED);
        finding(inspectionId, InspectionPerspectiveKind.ALL, PerspectiveOutcome.UNCHECKED, "WAIT_LIMIT_EXCEEDED", null);

        mockMvc.perform(bearer(get(BASE + "/" + inspectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judgment").value("UNJUDGED"))
                .andExpect(jsonPath("$.data.perspectives.length()").value(1))
                .andExpect(jsonPath("$.data.perspectives[0].perspective").value("ALL"))
                .andExpect(jsonPath("$.data.perspectives[0].outcome").value("UNCHECKED"))
                .andExpect(jsonPath("$.data.perspectives[0].skipReason").value("WAIT_LIMIT_EXCEEDED"));
    }

    @Test
    void 상세는_사유와_셀러_안내와_프롬프트_버전과_내용_해시를_준다() throws Exception {
        String contentHash = hash("d3");
        long inspectionId = settled(productId, contentHash, InspectionJudgment.FLAG);

        mockMvc.perform(bearer(get(BASE + "/" + inspectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("연어 건사료 2kg"))
                .andExpect(jsonPath("$.data.reasonCode").value("DISPLAY_ADS_EXAGGERATION"))
                .andExpect(jsonPath("$.data.reasonSummary").value("과장 광고 문구"))
                .andExpect(jsonPath("$.data.sellerNotice").value("문구를 고쳐 주세요."))
                .andExpect(jsonPath("$.data.promptVersion").value(PROMPT))
                .andExpect(jsonPath("$.data.contentHash").value(contentHash))
                .andExpect(jsonPath("$.data.policyVersion").value(POLICY));
    }

    /** 멈춘 건의 진단 단서. 어느 워커가 잡고 있고 언제 풀리는지를 상세에서만 준다. */
    @Test
    void 상세는_임차_정보를_주고_목록은_주지_않는다() throws Exception {
        long inspectionId = leased(productId, hash("d4"), "worker-1");

        mockMvc.perform(bearer(get(BASE + "/" + inspectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leaseOwner").value("worker-1"))
                .andExpect(jsonPath("$.data.leaseExpiresAt").exists());

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].leaseOwner").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].leaseExpiresAt").doesNotExist());
    }

    @Test
    void 판정이_끝난_건은_임차가_비어_있다() throws Exception {
        long inspectionId = settled(productId, hash("d5"), InspectionJudgment.PASS);

        mockMvc.perform(bearer(get(BASE + "/" + inspectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leaseOwner").doesNotExist())
                .andExpect(jsonPath("$.data.leaseExpiresAt").doesNotExist())
                .andExpect(jsonPath("$.data.settledAt").exists());
    }

    @Test
    void 없는_검수의_상세는_404_40508_을_반환한다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(40508));
    }

    @Test
    void 관점_기록이_없는_검수의_상세는_빈_관점_목록을_준다() throws Exception {
        long inspectionId = queued(productId, hash("d6"));

        mockMvc.perform(bearer(get(BASE + "/" + inspectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.perspectives.length()").value(0))
                .andExpect(jsonPath("$.data.judgment").doesNotExist());
    }

    // --- 픽스처 ---

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder) {
        return bearer(builder, adminToken);
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** 내용 해시는 CHAR(64)다. 유일 제약이 (대상, 해시, 정책)이라 행마다 다른 값을 심는다 */
    private static String hash(String seed) {
        return (seed + "0".repeat(64)).substring(0, 64);
    }

    private long queued(long targetRef, String contentHash) {
        return queueOn(InspectionTargetType.PRODUCT, targetRef, contentHash);
    }

    private long queueOn(InspectionTargetType targetType, long targetRef, String contentHash) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            ContentInspection inspection =
                    ContentInspection.Companion.queue(targetType, targetRef, contentHash, POLICY, 1);
            em.persist(inspection);
            em.flush();
            return inspection.getId();
        }));
    }

    /** 워커가 선점한 상태. 발행 경로가 적는 것과 같은 모양이다 */
    private long leased(long targetRef, String contentHash, String owner) {
        long id = queued(targetRef, contentHash);
        tx.executeWithoutResult(txStatus -> em.find(ContentInspection.class, id)
                .lease(owner, LocalDateTime.now(), Duration.ofMinutes(5), Duration.ofMinutes(10)));
        return id;
    }

    private long settled(long targetRef, String contentHash, InspectionJudgment judgment) {
        long id = queued(targetRef, contentHash);
        tx.executeWithoutResult(txStatus -> em.find(ContentInspection.class, id)
                .settle(judgment, PROMPT, "DISPLAY_ADS_EXAGGERATION", "과장 광고 문구", "문구를 고쳐 주세요.", LocalDateTime.now()));
        return id;
    }

    private void finding(
            long inspectionId,
            InspectionPerspectiveKind perspective,
            PerspectiveOutcome outcome,
            String skipReason,
            String violations) {
        tx.executeWithoutResult(txStatus ->
                em.persist(InspectionFinding.Companion.of(inspectionId, perspective, outcome, skipReason, violations)));
    }

    /**
     * 요청 시각을 뒤로 민다.
     *
     * <p>엔티티가 생성 시점의 현재 시각을 적고 갱신을 막아 두어(재시도로 밀리면 기한이 영원히 오지
     * 않는다) 네이티브로만 바꿀 수 있다. 정렬 축을 테스트가 쥐려면 이 통로가 필요하다.
     */
    private void backdateRequestedAt(long inspectionId, LocalDateTime requestedAt) {
        tx.executeWithoutResult(
                txStatus -> em.createNativeQuery("update content_inspections set requested_at = :at where id = :id")
                        .setParameter("at", requestedAt)
                        .setParameter("id", inspectionId)
                        .executeUpdate());
    }
}
