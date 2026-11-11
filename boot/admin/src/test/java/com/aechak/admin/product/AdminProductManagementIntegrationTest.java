package com.aechak.admin.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.audit.auditlog.AdminAuditLog;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.domain.product.category.Category;
import com.aechak.domain.product.option.OptionCombination;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.product.product.enums.SaleStatus;
import com.aechak.domain.product.report.ProductReport;
import com.aechak.domain.product.report.enums.ProductReportReason;
import com.aechak.domain.seller.seller.Seller;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.webcommon.trace.TraceIdFilter;
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
 * 어드민 상품 카탈로그 관리 API 통합 테스트 — 권한 경계, 전수 목록과 각 필터, 판매중지·재개,
 * 금지된 전이, 감사 기록을 HTTP로 검증한다.
 *
 * <p>가장 중요한 단언은 "검수 큐에 없는 상품도 목록에 나온다"와 "운영자가 막은 상품은 셀러 전이가
 * 거부된다" 둘이다. 앞이 깨지면 이 화면이 검수 큐의 복제가 되고, 뒤가 깨지면 조치가 셀러 요청
 * 한 번에 뒤집혀 API만 늘어난다. 뒤쪽의 HTTP 경로 검증은 셀러 모듈 테스트가 들고 있다.
 *
 * <p>MockMvc를 운영 체인과 같은 모양(추적 필터가 시큐리티보다 앞)으로 만들어 감사 행의 추적 id도 함께 본다.
 */
class AdminProductManagementIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/products";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;
    private long actorId;
    private String actorToken;
    private long sellerId;
    private long rootCategoryId;
    private long midCategoryId;
    private long leafCategoryId;

    @BeforeEach
    void setUpMockMvcAndFixtures() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new TraceIdFilter(), securityFilterChain)
                .build();
        actorId = createUser();
        actorToken = mintAccessToken(actorId);
        sellerId = seedSeller("애착상회");
        seedCategoryTree();
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
    void 일반_GENERAL_토큰은_판매중지도_403_20011_로_막히고_상태가_바뀌지_않는다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);

        mockMvc.perform(bearer(
                                post(BASE + "/" + productId + "/sale-suspensions"),
                                mintAccessToken(createUser(), UserRole.GENERAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"원산지 허위\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));

        assertThat(productOf(productId).getSaleStatus()).isEqualTo(SaleStatus.ON_SALE);
        assertThat(productOf(productId).getAdminSaleBlocked()).isFalse();
    }

    // --- 목록 ---

    /** 이 화면의 존재 이유다 — 검수 큐에 행이 없는 평범한 판매중 상품이 보여야 한다. */
    @Test
    void 검수_큐에_없는_상품도_목록에_나온다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(productId))
                .andExpect(jsonPath("$.data.items[0].inspectionStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.items[0].saleStatus").value("ON_SALE"));
    }

    @Test
    void 목록은_등록_역순으로_주고_전체_수를_함께_준다() throws Exception {
        String first = seedProduct("첫상품", leafCategoryId);
        String second = seedProduct("둘째상품", leafCategoryId);

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].productId").value(second))
                .andExpect(jsonPath("$.data.items[1].productId").value(first));
    }

    @Test
    void 목록_행은_상호명과_카테고리명과_가격과_신고_수를_채운다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        seedReports(productId, 3);

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productName").value("연어 건사료"))
                .andExpect(jsonPath("$.data.items[0].sellerId").value(sellerId))
                .andExpect(jsonPath("$.data.items[0].storeName").value("애착상회"))
                .andExpect(jsonPath("$.data.items[0].categoryName").value("건사료"))
                .andExpect(jsonPath("$.data.items[0].regularPrice").value(25_000))
                .andExpect(jsonPath("$.data.items[0].discountPrice").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].reportCount").value(3))
                .andExpect(jsonPath("$.data.items[0].adminSaleSuspended").value(false))
                .andExpect(jsonPath("$.data.items[0].adminSaleSuspendedAt").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].createdAt").exists());
    }

    @Test
    void 신고가_없는_상품도_목록에_남고_신고_수가_0이다() throws Exception {
        String reported = seedProduct("신고상품", leafCategoryId);
        seedReports(reported, 2);
        String clean = seedProduct("무신고상품", leafCategoryId);

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[0].productId").value(clean))
                .andExpect(jsonPath("$.data.items[0].reportCount").value(0))
                .andExpect(jsonPath("$.data.items[1].reportCount").value(2));
    }

    @Test
    void 셀러_행이_없는_상품도_목록에_남고_상호명이_빈다() throws Exception {
        String orphan = seedProduct("주인없는상품", leafCategoryId, 777L);

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(orphan))
                .andExpect(jsonPath("$.data.items[0].storeName").doesNotExist());
    }

    @Test
    void 검수_상태로_거를_수_있다() throws Exception {
        String approved = seedProduct("통과상품", leafCategoryId);
        String rejected = seedProduct("반려상품", leafCategoryId);
        rejectByInspection(rejected);

        mockMvc.perform(bearer(get(BASE).param("inspectionStatus", "REJECTED"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(rejected));

        mockMvc.perform(bearer(get(BASE).param("inspectionStatus", "approved"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(approved));
    }

    @Test
    void 판매_상태로_거를_수_있다() throws Exception {
        String onSale = seedProduct("판매중상품", leafCategoryId);
        String suspended = seedProduct("중지상품", leafCategoryId);
        suspend(suspended, "원산지 허위").andExpect(status().isNoContent());

        mockMvc.perform(bearer(get(BASE).param("saleStatus", "SUSPENDED"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(suspended));

        mockMvc.perform(bearer(get(BASE).param("saleStatus", "ON_SALE"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productId").value(onSale));
    }

    @Test
    void 알_수_없는_필터_값은_조건_없음으로_본다() throws Exception {
        seedProduct("상품", leafCategoryId);

        mockMvc.perform(bearer(get(BASE).param("inspectionStatus", "FOO").param("saleStatus", "BAR"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    /** 운영자는 대분류로도 훑는다 — 구매자 카탈로그가 중분류만 받는 것과 다르다. */
    @Test
    void 카테고리_필터는_준_노드의_하위까지_포함한다() throws Exception {
        String inTree = seedProduct("사료상품", leafCategoryId);
        long otherRoot = seedRootCategory("고양이");
        String outOfTree = seedProduct("고양이상품", otherRoot);

        mockMvc.perform(bearer(get(BASE).param("categoryId", String.valueOf(rootCategoryId)), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(inTree));

        mockMvc.perform(bearer(get(BASE).param("categoryId", String.valueOf(midCategoryId)), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(inTree));

        mockMvc.perform(bearer(get(BASE).param("categoryId", String.valueOf(otherRoot)), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(outOfTree));
    }

    @Test
    void 키워드는_상품명과_상호명을_함께_본다() throws Exception {
        String mine = seedProduct("연어 건사료", leafCategoryId);
        long otherSeller = seedSeller("다른상회");
        String others = seedProduct("닭가슴살", leafCategoryId, otherSeller);

        mockMvc.perform(bearer(get(BASE).param("keyword", "  연어  "), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(mine));

        mockMvc.perform(bearer(get(BASE).param("keyword", "다른상회"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(others));

        mockMvc.perform(bearer(get(BASE).param("keyword", "   "), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2));
    }

    @Test
    void 페이지_크기와_번호가_먹고_전체_수는_필터_기준이다() throws Exception {
        seedProduct("첫상품", leafCategoryId);
        String second = seedProduct("둘째상품", leafCategoryId);
        String third = seedProduct("셋째상품", leafCategoryId);

        mockMvc.perform(bearer(get(BASE).param("page", "0").param("size", "2"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].productId").value(third))
                .andExpect(jsonPath("$.data.items[1].productId").value(second));

        mockMvc.perform(bearer(get(BASE).param("page", "1").param("size", "2"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void 음수_페이지와_과대_크기는_400이_아니라_절삭된다() throws Exception {
        seedProduct("상품", leafCategoryId);

        mockMvc.perform(bearer(get(BASE).param("page", "-5").param("size", "9999"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    // --- 판매중지 ---

    @Test
    void 판매중지는_204_이고_판매_상태와_막음_표시를_남긴다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);

        suspend(productId, "  원산지 표기 허위  ").andExpect(status().isNoContent());

        Product saved = productOf(productId);
        assertThat(saved.getSaleStatus()).isEqualTo(SaleStatus.SUSPENDED);
        assertThat(saved.getAdminSaleBlocked()).isTrue();
        assertThat(saved.getAdminSaleBlockReason()).isEqualTo("원산지 표기 허위");
        assertThat(saved.getAdminSaleBlockedByAdminId()).isEqualTo(actorId);
        assertThat(saved.getAdminSaleBlockedAt()).isNotNull();
        assertThat(saved.getAdminSaleBlockPreviousStatus()).isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    void 판매중지는_목록에_막음_표시로_보인다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        suspend(productId, "원산지 허위").andExpect(status().isNoContent());

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].adminSaleSuspended").value(true))
                .andExpect(jsonPath("$.data.items[0].adminSaleSuspendedAt").exists())
                .andExpect(jsonPath("$.data.items[0].adminSaleSuspendReason").value("원산지 허위"))
                .andExpect(jsonPath("$.data.items[0].saleStatus").value("SUSPENDED"));
    }

    @Test
    void 이미_막힌_상품의_판매중지는_409_40012_이고_사유를_덮지_않는다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        suspend(productId, "1차 사유").andExpect(status().isNoContent());

        suspend(productId, "2차 사유")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(40012));

        assertThat(productOf(productId).getAdminSaleBlockReason()).isEqualTo("1차 사유");
    }

    @Test
    void 없는_상품의_판매중지는_404_40000_이다() throws Exception {
        suspend("01JZZZZZZZZZZZZZZZZZZZZZZZ", "사유")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(40000));
    }

    @Test
    void 사유를_비우면_400_90001_이고_상태가_바뀌지_않는다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);

        suspend(productId, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));

        assertThat(productOf(productId).getSaleStatus()).isEqualTo(SaleStatus.ON_SALE);
        assertThat(productOf(productId).getAdminSaleBlocked()).isFalse();
    }

    @Test
    void 셀러가_스스로_내려_둔_판매중지도_막을_수_있다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        changeSaleStatusBySeller(productId, SaleStatus.SUSPENDED);

        suspend(productId, "신고 누적").andExpect(status().isNoContent());

        Product saved = productOf(productId);
        assertThat(saved.getAdminSaleBlocked()).isTrue();
        assertThat(saved.getAdminSaleBlockPreviousStatus())
                .as("셀러 의사를 보관해야 재개가 그 자리로 되돌릴 수 있다")
                .isEqualTo(SaleStatus.SUSPENDED);
    }

    // --- 재개 ---

    @Test
    void 재개는_204_이고_막음_표시를_지우고_직전_상태로_되돌린다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        seedActiveStock(productId);
        suspend(productId, "원산지 허위").andExpect(status().isNoContent());

        resume(productId, "소명 접수").andExpect(status().isNoContent());

        Product saved = productOf(productId);
        assertThat(saved.getSaleStatus()).isEqualTo(SaleStatus.ON_SALE);
        assertThat(saved.getAdminSaleBlocked()).isFalse();
        assertThat(saved.getAdminSaleBlockReason()).isNull();
        assertThat(saved.getAdminSaleBlockedByAdminId()).isNull();
        assertThat(saved.getAdminSaleBlockPreviousStatus()).isNull();
    }

    /** 품절은 셀러가 지정하는 값이 아니라 재고에서 파생하는 값이다 — 되돌린 뒤 동기화가 맞춘다. */
    @Test
    void 재고가_없는_상품을_재개하면_품절로_내려간다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        seedEmptyStock(productId);
        suspend(productId, "원산지 허위").andExpect(status().isNoContent());

        resume(productId, null).andExpect(status().isNoContent());

        assertThat(productOf(productId).getSaleStatus()).isEqualTo(SaleStatus.OUT_OF_STOCK);
    }

    @Test
    void 셀러가_스스로_내려_둔_상품의_재개는_판매중으로_올리지_않는다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        seedActiveStock(productId);
        changeSaleStatusBySeller(productId, SaleStatus.SUSPENDED);
        suspend(productId, "신고 누적").andExpect(status().isNoContent());

        resume(productId, null).andExpect(status().isNoContent());

        assertThat(productOf(productId).getSaleStatus())
                .as("운영자 조치를 푸는 것이지 셀러 의사를 뒤집는 것이 아니다")
                .isEqualTo(SaleStatus.SUSPENDED);
    }

    @Test
    void 막혀_있지_않은_상품의_재개는_409_40009_이고_상태를_건드리지_않는다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);

        resume(productId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(40009));

        assertThat(productOf(productId).getSaleStatus()).isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    void 없는_상품의_재개는_404_40000_이다() throws Exception {
        resume("01JZZZZZZZZZZZZZZZZZZZZZZZ", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(40000));
    }

    @Test
    void 재개_요청은_본문이_없어도_받는다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        seedActiveStock(productId);
        suspend(productId, "원산지 허위").andExpect(status().isNoContent());

        mockMvc.perform(bearer(post(BASE + "/" + productId + "/sale-suspensions/release"), actorToken))
                .andExpect(status().isNoContent());

        assertThat(productOf(productId).getAdminSaleBlocked()).isFalse();
    }

    // --- 막음 표시가 셀러 전이를 막는다 ---

    /**
     * 이 작업의 핵심 단언. SaleStatus는 SUSPENDED를 셀러 지정 가능 값으로 열어 둬서, 막음 표시를
     * 보지 않으면 운영자 조치가 셀러의 다음 요청 한 번에 뒤집힌다. 셀러 HTTP 경로의 같은 검증은
     * 셀러 모듈 테스트가 들고 있다.
     */
    @Test
    void 막힌_상품은_셀러_전이가_40010_으로_거부된다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        suspend(productId, "원산지 허위").andExpect(status().isNoContent());

        assertThat(sellerTransitionErrorCode(productId, SaleStatus.ON_SALE)).isEqualTo(40010);
        assertThat(productOf(productId).getSaleStatus()).isEqualTo(SaleStatus.SUSPENDED);
    }

    @Test
    void 재개한_뒤에는_셀러_전이가_다시_통한다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        seedActiveStock(productId);
        suspend(productId, "원산지 허위").andExpect(status().isNoContent());
        resume(productId, null).andExpect(status().isNoContent());

        changeSaleStatusBySeller(productId, SaleStatus.SUSPENDED);

        assertThat(productOf(productId).getSaleStatus()).isEqualTo(SaleStatus.SUSPENDED);
    }

    // --- 감사 기록 ---

    @Test
    void 판매중지는_성공_행에_상품명과_사유를_남긴다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);

        suspend(productId, "원산지 표기 허위").andExpect(status().isNoContent());

        AdminAuditLog row = onlyAuditRow();
        assertThat(row.getActorUserId()).isEqualTo(actorId);
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.PRODUCT_SALE_SUSPENDED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.PRODUCT);
        assertThat(row.getTargetId()).isEqualTo(productId);
        assertThat(row.getTargetLabel()).isEqualTo("연어 건사료");
        assertThat(row.getReason()).isEqualTo("원산지 표기 허위");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(row.getHttpStatus()).isEqualTo(204);
        assertThat(row.getRequestId()).as("X-Trace-Id가 행에 들어오지 않았다").isNotBlank();
    }

    @Test
    void 재개도_자기_조치로_남는다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        seedActiveStock(productId);
        suspend(productId, "원산지 허위").andExpect(status().isNoContent());

        resume(productId, "소명 접수").andExpect(status().isNoContent());

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getAction()).isEqualTo(AdminAuditAction.PRODUCT_SALE_RESUMED);
        assertThat(rows.get(1).getTargetLabel()).isEqualTo("연어 건사료");
        assertThat(rows.get(1).getReason()).isEqualTo("소명 접수");
    }

    /** 거부당한 시도도 대상과 사유가 채워진 채 남아야 한다 — "하려 했지만 막혔다"가 요점인 조치다. */
    @Test
    void 거부된_판매중지_시도는_대상이_채워진_실패_행을_남긴다() throws Exception {
        String productId = seedProduct("연어 건사료", leafCategoryId);
        suspend(productId, "1차 사유").andExpect(status().isNoContent());

        suspend(productId, "2차 사유").andExpect(status().isConflict());

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getTargetId()).isEqualTo(productId);
        assertThat(rows.get(1).getTargetLabel()).isEqualTo("연어 건사료");
        assertThat(rows.get(1).getReason()).isEqualTo("2차 사유");
        assertThat(rows.get(1).getResult()).isEqualTo(AdminAuditResult.FAILURE);
        assertThat(rows.get(1).getHttpStatus()).isEqualTo(409);
    }

    @Test
    void 상품_목록_조회는_감사_행을_남기지_않는다() throws Exception {
        seedProduct("연어 건사료", leafCategoryId);

        mockMvc.perform(bearer(get(BASE), actorToken)).andExpect(status().isOk());

        assertThat(auditRows()).isEmpty();
    }

    // --- 보조 ---

    private ResultActions suspend(String productId, String reason) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + productId + "/sale-suspensions"), actorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":" + json(reason) + "}"));
    }

    private ResultActions resume(String productId, String reason) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + productId + "/sale-suspensions/release"), actorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":" + json(reason) + "}"));
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private long seedSeller(String storeName) {
        long userId = createUser();
        tx.executeWithoutResult(txStatus -> em.persist(Seller.Companion.open(userId, storeName, 0L)));
        return userId;
    }

    private void seedCategoryTree() {
        tx.executeWithoutResult(txStatus -> {
            Category root = Category.Companion.create(null, Category.ROOT_DEPTH, "강아지", null, 1);
            em.persist(root);
            Category mid = Category.Companion.create(root, Category.MID_DEPTH, "사료", null, 1);
            em.persist(mid);
            Category leaf = Category.Companion.create(mid, Category.LEAF_DEPTH, "건사료", null, 1);
            em.persist(leaf);
            em.flush();
            rootCategoryId = root.getId();
            midCategoryId = mid.getId();
            leafCategoryId = leaf.getId();
        });
    }

    private long seedRootCategory(String name) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            Category root = Category.Companion.create(null, Category.ROOT_DEPTH, name, null, 2);
            em.persist(root);
            em.flush();
            return root.getId();
        }));
    }

    private String seedProduct(String name, long categoryId) {
        return seedProduct(name, categoryId, sellerId);
    }

    private String seedProduct(String name, long categoryId, long ownerId) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            Product product = Product.Companion.register(
                    em.getReference(Category.class, categoryId),
                    ownerId,
                    name,
                    null,
                    null,
                    25_000L,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of());
            em.persist(product);
            em.flush();
            return product.getPublicId();
        }));
    }

    private void seedReports(String productPublicId, int count) {
        tx.executeWithoutResult(txStatus -> {
            Product product = findProduct(productPublicId);
            for (int i = 0; i < count; i++) {
                em.persist(ProductReport.Companion.report(product, 90_000L + i, ProductReportReason.FALSE_AD, null));
            }
        });
    }

    private void seedActiveStock(String productPublicId) {
        seedStock(productPublicId, 10);
    }

    private void seedEmptyStock(String productPublicId) {
        seedStock(productPublicId, 0);
    }

    private void seedStock(String productPublicId, int quantity) {
        tx.executeWithoutResult(txStatus -> em.persist(
                OptionCombination.Companion.create(findProduct(productPublicId), "기본", 0L, quantity, "-", List.of())));
    }

    private void rejectByInspection(String productPublicId) {
        tx.executeWithoutResult(txStatus -> findProduct(productPublicId).rejectByInspection());
    }

    /** 셀러 경로의 도메인 전이를 그대로 부른다 — 셀러 HTTP 경로는 어드민 모듈에 없다. */
    private void changeSaleStatusBySeller(String productPublicId, SaleStatus saleStatus) {
        tx.executeWithoutResult(txStatus -> findProduct(productPublicId).changeSaleStatusBySeller(saleStatus, false));
    }

    private int sellerTransitionErrorCode(String productPublicId, SaleStatus saleStatus) {
        try {
            changeSaleStatusBySeller(productPublicId, saleStatus);
            throw new AssertionError("셀러 전이가 거부되지 않았다");
        } catch (com.aechak.common.error.BusinessException e) {
            return e.getErrorCode().getCode();
        }
    }

    /** 저장 검증은 별도 트랜잭션 재조회로 한다 — 1차 캐시가 남은 상태로 보면 가짜로 통과한다. */
    private Product productOf(String productPublicId) {
        return tx.execute(txStatus -> findProduct(productPublicId));
    }

    private Product findProduct(String productPublicId) {
        return em.createQuery("select p from Product p where p.publicId = :publicId", Product.class)
                .setParameter("publicId", productPublicId)
                .getSingleResult();
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
