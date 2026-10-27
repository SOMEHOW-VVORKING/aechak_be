package com.aechak.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.audit.auditlog.AdminAuditLog;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetIdKind;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.domain.inspection.ProductReviewQueueItem;
import com.aechak.domain.product.category.Category;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.product.product.enums.InspectionStatus;
import com.aechak.domain.seller.seller.Seller;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * [통합] <b>공개 식별자(ULID)로 대상을 가리키는</b> 조치의 기록을 고정한다.
 *
 * <p>상품 검수는 어드민 경로가 상품을 공개 ULID로 가리키는 유일한 조치다. 대상 종류별 식별자
 * 규칙이 실제로 통하는지는 여기서만 확인된다 — 숫자 id를 적으려 하면 도메인 대조가 기록을
 * 통째로 버리고, 그 사고는 조치가 성공한 채로 조용히 일어난다.
 */
class AdminAuditProductRecordingIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/product-inspections";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;
    private String adminToken;
    private long sellerUserId;
    private long productId;
    private String productPublicId;

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
            product.rejectByInspection();
            em.persist(product);
            em.flush();
            productId = product.getId();
            productPublicId = product.getPublicId();
            em.persist(ProductReviewQueueItem.Companion.openForProduct(
                    productId, sellerUserId, "연어 건사료 2kg", LocalDateTime.now()));
        });
    }

    @Test
    void 검수_승인은_공개_ULID를_대상으로_적고_그때의_상품명을_남긴다() throws Exception {
        mockMvc.perform(bearer(post(BASE + "/" + productPublicId + "/approve"))).andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.PRODUCT_INSPECTION_APPROVED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.PRODUCT);
        assertThat(row.getTargetId()).isEqualTo(productPublicId);
        assertThat(AdminAuditTargetIdKind.PUBLIC_ULID.accepts(row.getTargetId()))
                .as("상품 대상 식별자가 공개 ULID가 아니다")
                .isTrue();
        assertThat(row.getTargetLabel()).isEqualTo("연어 건사료 2kg");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(inspectionStatus()).isEqualTo(InspectionStatus.APPROVED);
    }

    @Test
    void 검수_반려는_사유를_행에_남긴다() throws Exception {
        mockMvc.perform(bearer(post(BASE + "/" + productPublicId + "/reject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"치료 효능 표현을 빼 주세요\"}"))
                .andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.PRODUCT_INSPECTION_REJECTED);
        assertThat(row.getTargetId()).isEqualTo(productPublicId);
        assertThat(row.getReason()).isEqualTo("치료 효능 표현을 빼 주세요");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
    }

    /** 닫힌 행을 다시 처리하려는 시도도 대상이 채워진 채 남아야 한다 */
    @Test
    void 이미_처리된_검수의_재승인은_실패_행을_남기고_상태를_건드리지_않는다() throws Exception {
        mockMvc.perform(bearer(post(BASE + "/" + productPublicId + "/approve"))).andExpect(status().isNoContent());

        mockMvc.perform(bearer(post(BASE + "/" + productPublicId + "/approve"))).andExpect(status().isConflict());

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(2);
        AdminAuditLog failure = rows.get(1);
        assertThat(failure.getAction()).isEqualTo(AdminAuditAction.PRODUCT_INSPECTION_APPROVED);
        assertThat(failure.getTargetId()).isEqualTo(productPublicId);
        assertThat(failure.getResult()).isEqualTo(AdminAuditResult.FAILURE);
        assertThat(failure.getHttpStatus()).isEqualTo(409);
        assertThat(inspectionStatus()).isEqualTo(InspectionStatus.APPROVED);
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

    private InspectionStatus inspectionStatus() {
        return tx.execute(txStatus -> em.createQuery(
                        "select p.inspectionStatus from Product p where p.id = :id", InspectionStatus.class)
                .setParameter("id", productId)
                .getSingleResult());
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }
}
