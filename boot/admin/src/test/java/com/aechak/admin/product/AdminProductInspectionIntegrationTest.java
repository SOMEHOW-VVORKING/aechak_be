package com.aechak.admin.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.inspection.ContentInspection;
import com.aechak.domain.inspection.ProductReviewQueueItem;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import com.aechak.domain.inspection.enums.ReviewQueueStatus;
import com.aechak.domain.product.category.Category;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.product.product.enums.InspectionStatus;
import com.aechak.domain.seller.seller.Seller;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * [통합] 관리자 검토 큐 API를 고정함.
 *
 * 깨지면 관리자가 AI가 걸러 낸 상품을 볼 수 없거나, 결정이 상품 노출에 반영되지 않거나,
 * 셀러가 고쳐서 이미 닫힌 행을 관리자가 다시 처리해 상품 상태를 뒤집음.
 */
class AdminProductInspectionIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/product-inspections";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;
    private String adminToken;
    private long sellerUserId;
    private long productId;
    private String publicId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        adminToken = mintAccessToken(createUser());
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
                    "관절염 치료에 효과가 있습니다.",
                    "products/thumbnail.png",
                    25_000L,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of());
            product.holdInspection();
            em.persist(product);
            em.flush();
            productId = product.getId();
            publicId = product.getPublicId();

            ContentInspection inspection = ContentInspection.Companion.queue(
                    InspectionTargetType.PRODUCT, productId, "a".repeat(64), "policy-v1", 1);
            inspection.settle(
                    InspectionJudgment.FLAG,
                    "inspection-v1+policy-v1",
                    "MEDICAL_CLAIM",
                    "관절염 치료에 효과",
                    "치료 효능 표현을 고쳐 주세요.",
                    LocalDateTime.now());
            em.persist(inspection);
        });
    }

    @Test
    void 목록은_열린_검토_행을_상품명_셀러명_최근_판정과_함께_돌려준다() throws Exception {
        openReview();

        mockMvc.perform(bearer(get(BASE).param("status", "PENDING_REVIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(publicId))
                .andExpect(jsonPath("$.data.items[0].productName").value("연어 건사료 2kg"))
                .andExpect(jsonPath("$.data.items[0].sellerName").value("애착상회"))
                .andExpect(jsonPath("$.data.items[0].latestJudgment").value("FLAG"))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING_REVIEW"));
    }

    @Test
    void 상세는_AI가_본_상품_본문과_가장_최근_검수를_함께_돌려준다() throws Exception {
        openReview();

        mockMvc.perform(bearer(get(BASE + "/" + publicId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("관절염 치료에 효과가 있습니다."))
                .andExpect(jsonPath("$.data.categoryNames[0]").value("강아지"))
                .andExpect(jsonPath("$.data.categoryNames[2]").value("건사료"))
                .andExpect(jsonPath("$.data.awaitingAdminReview").value(true))
                .andExpect(jsonPath("$.data.judgment").value("FLAG"))
                .andExpect(jsonPath("$.data.reasonSummary").value("관절염 치료에 효과"))
                .andExpect(jsonPath("$.data.rejections").isEmpty());
    }

    @Test
    void 목록은_이의가_걸린_행만_걸러_준다() throws Exception {
        openReview();
        long appealedProductId = anotherProductWithAppeal();

        // 등재 사유에 값을 더하지 않고 "이의가 걸렸는가"를 별 축으로 뒀다. 이미 AI_DETECTION으로
        // 열린 행에 이의가 붙는데 그 값을 덮으면 경로별 유입량을 셀 수 없다
        mockMvc.perform(bearer(get(BASE).param("status", "PENDING_REVIEW").param("appealed", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(publicIdOf(appealedProductId)))
                .andExpect(jsonPath("$.data.items[0].appealedAt").exists());

        // 필터를 걸지 않으면 전체가 와야 한다
        mockMvc.perform(bearer(get(BASE).param("status", "PENDING_REVIEW")))
                .andExpect(jsonPath("$.data.totalCount").value(2));
    }

    @Test
    void 상세는_셀러가_적은_이의_사유를_함께_돌려준다() throws Exception {
        tx.executeWithoutResult(txStatus -> {
            ProductReviewQueueItem item = ProductReviewQueueItem.Companion.openForProduct(
                    productId, sellerUserId, "연어 건사료 2kg", LocalDateTime.now());
            item.appeal("치료 효능이 아니라 성분 설명입니다.", LocalDateTime.now());
            em.persist(item);
        });

        // 사유를 못 보면 관리자는 무엇에 답하는지 모른 채 승인·반려를 누른다
        mockMvc.perform(bearer(get(BASE + "/" + publicId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appealReason").value("치료 효능이 아니라 성분 설명입니다."))
                .andExpect(jsonPath("$.data.appealedAt").exists());
    }

    @Test
    void 승인하면_상품이_노출되고_검토_행이_승인으로_닫힌다() throws Exception {
        openReview();

        mockMvc.perform(bearer(post(BASE + "/" + publicId + "/approve"))).andExpect(status().isNoContent());

        assertEquals(InspectionStatus.APPROVED, storedStatus(), "승인했는데 숨겨져 있으면 관리자 결정이 장식이다");
        assertEquals(List.of(ReviewQueueStatus.APPROVED), queueStatuses(), "닫히지 않으면 같은 상품이 큐에 계속 남는다");
    }

    @Test
    void 반려하면_상품이_반려되고_사유가_반려_이력에_남는다() throws Exception {
        openReview();

        mockMvc.perform(rejectRequest("효능 표현을 빼고 다시 등록해 주세요.")).andExpect(status().isNoContent());

        assertEquals(InspectionStatus.REJECTED, storedStatus());
        assertEquals(List.of(ReviewQueueStatus.REJECTED), queueStatuses());
        mockMvc.perform(bearer(get(BASE + "/" + publicId)))
                .andExpect(jsonPath("$.data.rejections[0].reason").value("효능 표현을 빼고 다시 등록해 주세요."))
                .andExpect(jsonPath("$.data.awaitingAdminReview").value(false));
    }

    @Test
    void 열린_검토_행이_없으면_승인과_반려는_409_40500이고_상품_상태는_그대로다() throws Exception {
        mockMvc.perform(bearer(post(BASE + "/" + publicId + "/approve")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(40500));
        mockMvc.perform(rejectRequest("이미 처리된 건"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(40500));

        assertEquals(InspectionStatus.PENDING, storedStatus(), "상품을 먼저 바꾸고 행을 찾으므로 409에서 롤백되지 않으면 결정 없이 상품이 풀린다");
    }

    @Test
    void 반려_사유가_비어_있으면_400_90001() throws Exception {
        openReview();

        mockMvc.perform(rejectRequest(" "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));
    }

    @Test
    void 없는_상품은_404_40000() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/01ARZ3NDEKTSV4RRFFQ69G5FAV")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(40000));
    }

    /** 이의가 걸린 열린 행을 가진 내 상품을 하나 더 심고 그 id를 돌려준다 */
    private long anotherProductWithAppeal() {
        return tx.execute(txStatus -> {
            Category leaf = em.createQuery("select c from Category c where c.depth = 3", Category.class)
                    .setMaxResults(1)
                    .getSingleResult();
            Product product = Product.Companion.register(
                    leaf,
                    sellerUserId,
                    "오리 건사료 1kg",
                    "우리 아이 주식으로 좋습니다.",
                    "products/thumbnail2.png",
                    19_000L,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of());
            product.holdInspection();
            em.persist(product);
            em.flush();
            ProductReviewQueueItem item = ProductReviewQueueItem.Companion.openForProduct(
                    product.getId(), sellerUserId, "오리 건사료 1kg", LocalDateTime.now());
            item.appeal("성분 설명입니다.", LocalDateTime.now());
            em.persist(item);
            return product.getId();
        });
    }

    private String publicIdOf(long target) {
        return tx.execute(txStatus -> em.createQuery("select p.publicId from Product p where p.id = :id", String.class)
                .setParameter("id", target)
                .getSingleResult());
    }

    private void openReview() {
        tx.executeWithoutResult(txStatus -> em.persist(ProductReviewQueueItem.Companion.openForProduct(
                productId, sellerUserId, "연어 건사료 2kg", LocalDateTime.now())));
    }

    private InspectionStatus storedStatus() {
        return tx.execute(txStatus -> em.createQuery(
                        "select p.inspectionStatus from Product p where p.id = :id", InspectionStatus.class)
                .setParameter("id", productId)
                .getSingleResult());
    }

    private List<ReviewQueueStatus> queueStatuses() {
        return tx.execute(txStatus -> em.createQuery(
                        "select q.status from ProductReviewQueueItem q where q.targetId = :id order by q.id",
                        ReviewQueueStatus.class)
                .setParameter("id", productId)
                .getResultList());
    }

    private MockHttpServletRequestBuilder rejectRequest(String reason) {
        return bearer(post(BASE + "/" + publicId + "/reject"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + reason + "\"}");
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }
}
