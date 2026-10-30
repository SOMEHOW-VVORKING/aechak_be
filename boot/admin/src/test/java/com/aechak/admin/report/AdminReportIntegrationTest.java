package com.aechak.admin.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.application.report.admin.port.ReportReviewVisibilityPort;
import com.aechak.domain.product.category.Category;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.product.product.enums.InspectionStatus;
import com.aechak.domain.product.report.ProductReport;
import com.aechak.domain.product.report.enums.ProductReportReason;
import com.aechak.domain.report.decision.ReportDecision;
import com.aechak.domain.review.report.ReviewReport;
import com.aechak.domain.review.report.enums.ReviewReportReason;
import com.aechak.domain.review.review.Review;
import com.aechak.domain.review.review.enums.ReviewStatus;
import com.aechak.domain.seller.seller.Seller;
import com.aechak.domain.user.report.UserReport;
import com.aechak.domain.user.report.enums.ReportReasonCode;
import com.aechak.domain.user.sanction.UserSanction;
import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
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
 * 어드민 신고 조회·제재 API 통합 테스트.
 *
 * <p>처리 요청은 응답 코드만 보지 않고 부작용까지 본다 — 대상이 실제로 비노출로 갔는지, 제재 이력이
 * 남았는지, 거절된 요청이 결정 기록을 남기지 않았는지. 기록만 보고 통과시키면 위임이 끊겨도 초록이 된다.
 */
class AdminReportIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/reports";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    private ReportReviewVisibilityPort reviewVisibilityPort;

    private MockMvc mockMvc;
    private long adminId;
    private String adminToken;
    private long sellerUserId;
    private long authorId;
    private long productId;
    private String productPublicId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        adminId = createMember("운영자", UserRole.ADMIN);
        adminToken = mintAccessToken(adminId);
        sellerUserId = createMember("보리펫샵사장", UserRole.GENERAL);
        authorId = createMember("초코집사", UserRole.GENERAL);
        tx.executeWithoutResult(txStatus -> {
            em.persist(Seller.Companion.open(sellerUserId, "보리펫샵", 3_000L));
            Category root = Category.Companion.create(null, Category.ROOT_DEPTH, "강아지", null, 1);
            em.persist(root);
            Category mid = Category.Companion.create(root, Category.MID_DEPTH, "사료", null, 1);
            em.persist(mid);
            Category leaf = Category.Companion.create(mid, Category.LEAF_DEPTH, "건사료", null, 1);
            em.persist(leaf);
            Product product = Product.Companion.register(
                    leaf,
                    sellerUserId,
                    "저알러지 건식사료 5kg",
                    null,
                    "products/thumb.png",
                    25_000L,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of());
            em.persist(product);
            em.flush();
            productId = product.getId();
            productPublicId = product.getPublicId();
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
        long generalId = createMember("일반", UserRole.GENERAL);

        mockMvc.perform(bearer(get(BASE), mintAccessToken(generalId, UserRole.GENERAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }

    @Test
    void 일반_GENERAL_토큰은_처리도_403_으로_막히고_대상도_기록도_바뀌지_않는다() throws Exception {
        long reviewId = persistReview(1001L, "욕설이 섞인 리뷰");
        long reportId = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");
        long generalId = createMember("일반", UserRole.GENERAL);

        mockMvc.perform(bearer(
                        decisionRequest("REVIEW", reportId, hideBody("ABUSE")),
                        mintAccessToken(generalId, UserRole.GENERAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));

        assertThat(reviewStatusOf(reviewId)).isEqualTo(ReviewStatus.PUBLIC);
        assertThat(decisions()).isEmpty();
    }

    // --- 목록 ---

    @Test
    void 세_종류의_신고가_한_목록에_접수_역순으로_나온다() throws Exception {
        long productReportId = persistProductReport(ProductReportReason.FALSE_AD, "과장광고예요");
        long reviewId = persistReview(1001L, "욕설이 섞인 리뷰");
        long reviewReportId = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");
        long targetUserId = createMember("신고당한사람", UserRole.GENERAL);
        long userReportId = persistUserReport(targetUserId, ReportReasonCode.SPAM, "도배해요");

        mockMvc.perform(bearer(get(BASE), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].targetType").value("USER"))
                .andExpect(jsonPath("$.data.items[0].reportId").value(userReportId))
                .andExpect(jsonPath("$.data.items[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.items[0].targetOwnerNickname").value("신고당한사람"))
                .andExpect(jsonPath("$.data.items[1].targetType").value("REVIEW"))
                .andExpect(jsonPath("$.data.items[1].reportId").value(reviewReportId))
                .andExpect(jsonPath("$.data.items[1].targetName").value("욕설이 섞인 리뷰"))
                .andExpect(jsonPath("$.data.items[1].targetOwnerNickname").value("초코집사"))
                .andExpect(jsonPath("$.data.items[2].targetType").value("PRODUCT"))
                .andExpect(jsonPath("$.data.items[2].reportId").value(productReportId))
                .andExpect(jsonPath("$.data.items[2].targetKey").value(productPublicId))
                .andExpect(jsonPath("$.data.items[2].targetName").value("저알러지 건식사료 5kg"))
                .andExpect(jsonPath("$.data.items[2].reasonCode").value("FALSE_AD"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 대상_종류_필터는_그_종류만_준다() throws Exception {
        persistProductReport(ProductReportReason.ILLEGAL, "불법이에요");
        long reviewId = persistReview(1001L, "리뷰 본문");
        persistReviewReport(reviewId, ReviewReportReason.SPAM, "광고예요");

        mockMvc.perform(bearer(get(BASE).param("targetType", "PRODUCT"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].targetType").value("PRODUCT"));
    }

    @Test
    void 알_수_없는_종류_필터는_400이_아니라_전체_목록을_준다() throws Exception {
        persistProductReport(ProductReportReason.ILLEGAL, "불법이에요");

        mockMvc.perform(bearer(get(BASE).param("targetType", "오타").param("size", "999"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    void 처리_상태_필터는_미처리와_종결을_가른다() throws Exception {
        long dismissed = persistProductReport(ProductReportReason.FALSE_AD, "과장광고예요");
        long untouched = persistProductReport(ProductReportReason.ILLEGAL, "불법이에요");
        decide("PRODUCT", dismissed, dismissBody()).andExpect(status().isNoContent());

        mockMvc.perform(bearer(get(BASE).param("status", "RECEIVED"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].reportId").value(untouched));

        mockMvc.perform(bearer(get(BASE).param("status", "DISMISSED"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].reportId").value(dismissed))
                .andExpect(jsonPath("$.data.items[0].decision").value("DISMISS"));

        mockMvc.perform(bearer(get(BASE).param("status", "SANCTIONED"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    void 접수일_상한은_그날_접수분을_빼지_않는다() throws Exception {
        persistProductReport(ProductReportReason.FALSE_AD, "과장광고예요");
        String today = LocalDateTime.now().toLocalDate().toString();

        mockMvc.perform(bearer(get(BASE).param("reportedFrom", today).param("reportedTo", today), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));

        String yesterday = LocalDateTime.now().minusDays(1).toLocalDate().toString();
        mockMvc.perform(bearer(get(BASE).param("reportedTo", yesterday), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 커서로_세_표를_이어_받으면_중복도_누락도_없다() throws Exception {
        long productReportId = persistProductReport(ProductReportReason.FALSE_AD, "과장광고예요");
        long reviewId = persistReview(1001L, "리뷰 본문");
        long reviewReportId = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");
        long targetUserId = createMember("신고당한사람", UserRole.GENERAL);
        long userReportId = persistUserReport(targetUserId, ReportReasonCode.SPAM, "도배해요");

        String cursor = cursorOf(mockMvc.perform(bearer(get(BASE).param("size", "2"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].targetType").value("USER"))
                .andExpect(jsonPath("$.data.items[0].reportId").value(userReportId))
                .andExpect(jsonPath("$.data.items[1].targetType").value("REVIEW"))
                .andExpect(jsonPath("$.data.items[1].reportId").value(reviewReportId))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").exists()));

        mockMvc.perform(bearer(get(BASE).param("size", "2").param("cursor", cursor), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].targetType").value("PRODUCT"))
                .andExpect(jsonPath("$.data.items[0].reportId").value(productReportId))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void 조건을_바꾼_옛_커서는_400_90002_로_끊는다() throws Exception {
        persistProductReport(ProductReportReason.FALSE_AD, "과장광고예요");
        long reviewId = persistReview(1001L, "리뷰 본문");
        persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");

        String cursor = cursorOf(mockMvc.perform(bearer(get(BASE).param("size", "1"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").exists()));

        // 같은 조건에서는 그 커서가 통한다 — 아래 400 이 커서 고장이 아니라 지문 불일치임을 가린다
        mockMvc.perform(bearer(get(BASE).param("size", "1").param("cursor", cursor), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        mockMvc.perform(bearer(
                        get(BASE)
                                .param("size", "1")
                                .param("targetType", "PRODUCT")
                                .param("cursor", cursor),
                        adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90002));
    }

    @Test
    void 망가진_커서는_400_90002_를_반환한다() throws Exception {
        mockMvc.perform(bearer(get(BASE).param("cursor", "!!broken!!"), adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90002));
    }

    @Test
    void 같은_대상의_누적_신고_수를_함께_준다() throws Exception {
        persistProductReport(ProductReportReason.FALSE_AD, "과장광고예요");
        persistProductReport(ProductReportReason.ILLEGAL, "불법이에요");

        mockMvc.perform(bearer(get(BASE), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].reportCount").value(2))
                .andExpect(jsonPath("$.data.items[1].reportCount").value(2));
    }

    // --- 상세 ---

    @Test
    void 상세는_같은_대상의_이력을_주고_보고_있는_건은_뺀다() throws Exception {
        long first = persistProductReport(ProductReportReason.FALSE_AD, "과장광고예요");
        long second = persistProductReport(ProductReportReason.ILLEGAL, "불법이에요");

        mockMvc.perform(bearer(get(BASE + "/PRODUCT/" + second), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.reportId").value(second))
                .andExpect(jsonPath("$.data.report.reportCount").value(2))
                .andExpect(jsonPath("$.data.reasonText").value("불법이에요"))
                .andExpect(jsonPath("$.data.history.length()").value(1))
                .andExpect(jsonPath("$.data.history[0].reportId").value(first))
                .andExpect(jsonPath("$.data.history[0].reasonCode").value("FALSE_AD"))
                .andExpect(jsonPath("$.data.decision").doesNotExist());
    }

    @Test
    void 없는_신고의_상세는_404_140000_이다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/PRODUCT/999999"), adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(140000));
    }

    @Test
    void 알_수_없는_대상_종류_경로는_400_90001_이다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/SNS_POST/1"), adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));
    }

    // --- 처리: 부작용까지 확인한다 ---

    @Test
    void 리뷰_신고의_비공개_전환은_리뷰를_실제로_블라인드한다() throws Exception {
        long reviewId = persistReview(1001L, "욕설이 섞인 리뷰");
        long reportId = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");

        decide("REVIEW", reportId, hideBody("ABUSE")).andExpect(status().isNoContent());

        assertThat(reviewStatusOf(reviewId)).isEqualTo(ReviewStatus.BLOCKED);
        List<ReportDecision> decisions = decisions();
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getActedByAdminId()).isEqualTo(adminId);
        assertThat(decisions.get(0).getTargetOwnerUserId()).isEqualTo(authorId);
        assertThat(sanctionsOf(authorId)).isEmpty();

        mockMvc.perform(bearer(get(BASE + "/REVIEW/" + reportId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.status").value("SANCTIONED"))
                .andExpect(jsonPath("$.data.decision.decision").value("HIDE_TARGET"))
                .andExpect(jsonPath("$.data.decision.internalMemo").value("1차 조치"))
                .andExpect(jsonPath("$.data.decision.actedByAdminId").value(adminId));
    }

    @Test
    void 상품_신고에는_비공개_전환을_걸_수_없고_400_140002_이며_노출도_기록도_그대로다() throws Exception {
        long reportId = persistProductReport(ProductReportReason.ILLEGAL, "불법이에요");

        decide("PRODUCT", reportId, hideBody("ILLEGAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(140002));

        assertThat(inspectionStatusOf(productId)).isEqualTo(InspectionStatus.APPROVED);
        assertThat(decisions()).isEmpty();
    }

    @Test
    void 계정_제재는_대상을_가리고_제재_이력과_계정_상태를_함께_바꾼다() throws Exception {
        long reviewId = persistReview(1001L, "욕설이 섞인 리뷰");
        long reportId = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");

        decide("REVIEW", reportId, sanctionBody("ABUSE", "RESTRICT", "ABUSIVE_REVIEW", null))
                .andExpect(status().isNoContent());

        assertThat(reviewStatusOf(reviewId)).isEqualTo(ReviewStatus.BLOCKED);
        assertThat(statusOf(authorId)).isEqualTo(UserStatus.RESTRICTED);
        List<UserSanction> sanctions = sanctionsOf(authorId);
        assertThat(sanctions).hasSize(1);
        assertThat(sanctions.get(0).getActedByAdminId()).isEqualTo(adminId);
        assertThat(sanctions.get(0).getMemo()).isEqualTo("1차 조치");
    }

    @Test
    void 상품_신고의_계정_제재는_셀러를_제재하되_상품_노출은_건드리지_않는다() throws Exception {
        long reportId = persistProductReport(ProductReportReason.ILLEGAL, "불법이에요");

        decide("PRODUCT", reportId, sanctionBody("ILLEGAL", "RESTRICT", "OTHER", null))
                .andExpect(status().isNoContent());

        assertThat(statusOf(sellerUserId)).isEqualTo(UserStatus.RESTRICTED);
        assertThat(sanctionsOf(sellerUserId)).hasSize(1);
        assertThat(decisions().get(0).getTargetOwnerUserId()).isEqualTo(sellerUserId);
        // 상품 비노출 수단이 아직 없다 — 셀러 제재가 진열을 내리지는 않는다
        assertThat(inspectionStatusOf(productId)).isEqualTo(InspectionStatus.APPROVED);
    }

    @Test
    void 사용자_신고를_제재_없이_종결하면_계정이_그대로다() throws Exception {
        long targetUserId = createMember("신고당한사람", UserRole.GENERAL);
        long reportId = persistUserReport(targetUserId, ReportReasonCode.ABUSE, "욕설해요");

        decide("USER", reportId, dismissBody()).andExpect(status().isNoContent());

        assertThat(statusOf(targetUserId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionsOf(targetUserId)).isEmpty();
        assertThat(decisions()).hasSize(1);
    }

    @Test
    void 사용자_신고의_계정_제재는_대상_본인을_제재한다() throws Exception {
        long targetUserId = createMember("신고당한사람", UserRole.GENERAL);
        long reportId = persistUserReport(targetUserId, ReportReasonCode.ABUSE, "욕설해요");

        decide("USER", reportId, sanctionBody("ABUSE", "SUSPEND", "ABUSIVE_REVIEW", 7))
                .andExpect(status().isNoContent());

        assertThat(statusOf(targetUserId)).isEqualTo(UserStatus.SUSPENDED);
        assertThat(sanctionsOf(targetUserId)).hasSize(1);
    }

    @Test
    void 제재_없이_종결하면_대상은_그대로_노출된다() throws Exception {
        long reviewId = persistReview(1001L, "문제 없는 리뷰");
        long reportId = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설인가요");

        decide("REVIEW", reportId, dismissBody()).andExpect(status().isNoContent());

        assertThat(reviewStatusOf(reviewId)).isEqualTo(ReviewStatus.PUBLIC);
        assertThat(sanctionsOf(authorId)).isEmpty();
        assertThat(decisions()).hasSize(1);
    }

    @Test
    void 같은_신고를_두_번_처리하면_409_140001_이고_두_번째는_아무것도_바꾸지_않는다() throws Exception {
        long reviewId = persistReview(1001L, "욕설이 섞인 리뷰");
        long reportId = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");
        decide("REVIEW", reportId, dismissBody()).andExpect(status().isNoContent());

        decide("REVIEW", reportId, hideBody("ABUSE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(140001));

        assertThat(reviewStatusOf(reviewId)).isEqualTo(ReviewStatus.PUBLIC);
        assertThat(decisions()).hasSize(1);
        assertThat(decisions().get(0).getDecision().name()).isEqualTo("DISMISS");
    }

    @Test
    void 사용자_신고에는_비공개_전환을_걸_수_없고_400_140002_이며_기록도_남지_않는다() throws Exception {
        long targetUserId = createMember("신고당한사람", UserRole.GENERAL);
        long reportId = persistUserReport(targetUserId, ReportReasonCode.ABUSE, "욕설해요");

        decide("USER", reportId, hideBody("ABUSE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(140002));

        assertThat(decisions()).isEmpty();
        assertThat(statusOf(targetUserId)).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 위반_사유로_제재_없이_종결하면_400_140005_이다() throws Exception {
        long reportId = persistProductReport(ProductReportReason.ILLEGAL, "불법이에요");

        decide("PRODUCT", reportId, body("DISMISS", "ILLEGAL", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(140005));

        assertThat(decisions()).isEmpty();
    }

    @Test
    void 확인_불가_사유로_제재하면_400_140005_이고_대상은_그대로다() throws Exception {
        long reviewId = persistReview(1001L, "욕설이 섞인 리뷰");
        long reportId = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");

        decide("REVIEW", reportId, body("HIDE_TARGET", "NOT_CONFIRMED", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(140005));

        assertThat(reviewStatusOf(reviewId)).isEqualTo(ReviewStatus.PUBLIC);
        assertThat(decisions()).isEmpty();
    }

    @Test
    void 계정_제재인데_제재_내용이_없으면_400_140003_이다() throws Exception {
        long reportId = persistProductReport(ProductReportReason.ILLEGAL, "불법이에요");

        decide("PRODUCT", reportId, body("SANCTION_ACCOUNT", "ILLEGAL", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(140003));

        assertThat(inspectionStatusOf(productId)).isEqualTo(InspectionStatus.APPROVED);
        assertThat(decisions()).isEmpty();
    }

    @Test
    void 제재가_아닌_처리에_제재_내용을_보내면_400_140004_이다() throws Exception {
        long reportId = persistProductReport(ProductReportReason.ILLEGAL, "불법이에요");

        decide("PRODUCT", reportId, body("DISMISS", "NOT_CONFIRMED", null, "RESTRICT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(140004));

        assertThat(decisions()).isEmpty();
    }

    @Test
    void 제재_위임이_거절되면_결정_기록도_대상도_되돌아간다() throws Exception {
        long otherAdminId = createMember("부운영자", UserRole.ADMIN);
        long reportId = persistUserReport(otherAdminId, ReportReasonCode.ABUSE, "욕설해요");

        decide("USER", reportId, sanctionBody("ABUSE", "BAN", "ABUSIVE_REVIEW", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30021));

        assertThat(statusOf(otherAdminId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionsOf(otherAdminId)).isEmpty();
        assertThat(decisions()).isEmpty();
    }

    @Test
    void 이미_블라인드된_리뷰의_신고도_처리할_수_있고_기록이_남는다() throws Exception {
        long reviewId = persistReview(1001L, "욕설이 섞인 리뷰");
        long first = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");
        long second = persistReviewReport(reviewId, ReviewReportReason.SPAM, "광고도 섞였어요");
        decide("REVIEW", first, hideBody("ABUSE")).andExpect(status().isNoContent());
        assertThat(reviewStatusOf(reviewId)).isEqualTo(ReviewStatus.BLOCKED);

        decide("REVIEW", second, hideBody("ABUSE")).andExpect(status().isNoContent());

        assertThat(reviewStatusOf(reviewId)).isEqualTo(ReviewStatus.BLOCKED);
        assertThat(decisions()).hasSize(2);
    }

    /**
     * 비노출 판정의 세 답(노출 중·이미 가려짐·대상 없음)을 실제 어댑터로 못박는다.
     *
     * <p>Facade 쪽 계약은 대역으로 보므로 "행이 없으면 null"을 어댑터가 지키지 않으면 아무도 잡지
     * 못한다 — null을 true로 접어 버리면 대상이 사라진 신고가 "이미 가려짐"으로 통과해, 숨길 것이
     * 없는데도 계정 제재까지 확정된다. 대상 없음은 조회 경로가 대상 표를 inner join해 404(140000)로
     * 먼저 걸러지므로, HTTP 표면으로는 이 축을 태울 수 없다.
     */
    @Test
    void 비노출_판정_어댑터는_노출_중과_가려짐과_대상_없음을_구분한다() throws Exception {
        long reviewId = persistReview(1001L, "욕설이 섞인 리뷰");
        long reportId = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");

        assertThat(lockAndReadHidden(reviewId)).as("노출 중인 리뷰").isFalse();

        decide("REVIEW", reportId, hideBody("ABUSE")).andExpect(status().isNoContent());

        assertThat(lockAndReadHidden(reviewId)).as("블라인드된 리뷰").isTrue();
        assertThat(lockAndReadHidden(reviewId + 10_000L))
                .as("행이 없으면 null 이어야 한다 — true 로 접으면 숨길 대상 없이 제재가 나간다")
                .isNull();
    }

    /** 잠금 조회는 트랜잭션 안에서만 할 수 있다. */
    private Boolean lockAndReadHidden(long reviewId) {
        return tx.execute(txStatus -> reviewVisibilityPort.lockAndReadHidden(reviewId));
    }

    @Test
    void 접수_시각이_같은_여섯_건을_커서로_나눠_받아도_중복도_누락도_없다() throws Exception {
        long productA = persistProductReport(ProductReportReason.FALSE_AD, "과장광고예요");
        long productB = persistProductReport(ProductReportReason.ILLEGAL, "불법이에요");
        long reviewId = persistReview(1001L, "리뷰 본문");
        long reviewA = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");
        long reviewB = persistReviewReport(reviewId, ReviewReportReason.SPAM, "광고예요");
        long targetUserId = createMember("신고당한사람", UserRole.GENERAL);
        long userA = persistUserReport(targetUserId, ReportReasonCode.ABUSE, "욕설해요");
        long userB = persistUserReport(targetUserId, ReportReasonCode.SPAM, "도배해요");

        // 접수 시각을 같은 마이크로초로 맞춰 시각 축의 타이브레이크를 없앤다 —
        // keyset 에서 종류·id 조건이 빠지면 여기서 행이 빠지거나 두 번 나온다
        LocalDateTime sameInstant = LocalDateTime.now().withNano(123_000_000);
        setReportedAt("product_reports", productA, sameInstant);
        setReportedAt("product_reports", productB, sameInstant);
        setReportedAt("review_reports", reviewA, sameInstant);
        setReportedAt("review_reports", reviewB, sameInstant);
        setReportedAt("user_reports", userA, sameInstant);
        setReportedAt("user_reports", userB, sameInstant);

        // 전역 순서는 접수 역순 -> 종류 이름순 -> id 역순이다
        List<String> expected = List.of(
                "PRODUCT:" + productB,
                "PRODUCT:" + productA,
                "REVIEW:" + reviewB,
                "REVIEW:" + reviewA,
                "USER:" + userB,
                "USER:" + userA);

        assertThat(pageThrough(1)).containsExactlyElementsOf(expected);
        assertThat(pageThrough(2)).containsExactlyElementsOf(expected);
        assertThat(pageThrough(4)).containsExactlyElementsOf(expected);
    }

    @Test
    void 제재_상세가_필요한_사유인데_비어_있으면_회원_제재_도메인이_막고_전부_되돌아간다() throws Exception {
        long reviewId = persistReview(1001L, "욕설이 섞인 리뷰");
        long reportId = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");
        String withoutDetail = "{\"decision\":\"SANCTION_ACCOUNT\",\"reasonCode\":\"ABUSE\","
                + "\"reasonDetail\":\"확인했습니다\",\"noticeToOwner\":null,\"internalMemo\":null,"
                + "\"sanction\":{\"action\":\"RESTRICT\",\"reasonCode\":\"OTHER\","
                + "\"reasonDetail\":null,\"durationDays\":null}}";

        decide("REVIEW", reportId, withoutDetail)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(30800));

        assertThat(reviewStatusOf(reviewId)).isEqualTo(ReviewStatus.PUBLIC);
        assertThat(sanctionsOf(authorId)).isEmpty();
        assertThat(decisions()).isEmpty();
    }

    @Test
    void 제재가_거절되면_이미_걸린_리뷰_비노출까지_되돌아간다() throws Exception {
        // 관리자 계정이 쓴 리뷰 — 비노출은 통과하고 그 뒤 계정 제재가 30021 로 거절된다
        long adminAuthorId = createMember("부운영자", UserRole.ADMIN);
        long reviewId = persistReviewBy(adminAuthorId, 2002L, "욕설이 섞인 리뷰");
        long reportId = persistReviewReport(reviewId, ReviewReportReason.ABUSE, "욕설이에요");

        decide("REVIEW", reportId, sanctionBody("ABUSE", "BAN", "ABUSIVE_REVIEW", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30021));

        assertThat(reviewStatusOf(reviewId)).isEqualTo(ReviewStatus.PUBLIC);
        assertThat(statusOf(adminAuthorId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionsOf(adminAuthorId)).isEmpty();
        assertThat(decisions()).isEmpty();
    }

    // --- 요청 본문 ---

    private ResultActions decide(String targetType, long reportId, String body) throws Exception {
        return mockMvc.perform(bearer(decisionRequest(targetType, reportId, body), adminToken));
    }

    private MockHttpServletRequestBuilder decisionRequest(String targetType, long reportId, String body) {
        return post(BASE + "/" + targetType + "/" + reportId + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String dismissBody() {
        return body("DISMISS", "NOT_CONFIRMED", null, null);
    }

    private String hideBody(String reasonCode) {
        return body("HIDE_TARGET", reasonCode, null, null);
    }

    private String sanctionBody(String reasonCode, String action, String sanctionReason, Integer durationDays) {
        return "{\"decision\":\"SANCTION_ACCOUNT\",\"reasonCode\":\"" + reasonCode
                + "\",\"reasonDetail\":\"확인했습니다\",\"noticeToOwner\":\"안내합니다\",\"internalMemo\":\"1차 조치\","
                + "\"sanction\":{\"action\":\"" + action + "\",\"reasonCode\":\"" + sanctionReason
                + "\",\"reasonDetail\":\"위반을 확인했습니다\",\"durationDays\":"
                + (durationDays == null ? "null" : durationDays) + "}}";
    }

    /** sanctionAction 이 있으면 제재 내용을 함께 싣는다 — 조합 검증용. */
    private String body(String decision, String reasonCode, String reasonDetail, String sanctionAction) {
        String sanction = sanctionAction == null
                ? "null"
                : "{\"action\":\"" + sanctionAction + "\",\"reasonCode\":\"ABUSIVE_REVIEW\","
                        + "\"reasonDetail\":null,\"durationDays\":null}";
        return "{\"decision\":\"" + decision + "\",\"reasonCode\":\"" + reasonCode + "\",\"reasonDetail\":"
                + (reasonDetail == null ? "null" : "\"" + reasonDetail + "\"")
                + ",\"noticeToOwner\":\"안내합니다\",\"internalMemo\":\"1차 조치\",\"sanction\":" + sanction + "}";
    }

    /** 커서를 꺼낸다. 호출부가 nextCursor 존재를 먼저 단언한 뒤 쓴다. */
    private String cursorOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\"nextCursor\":\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).as("nextCursor 가 응답에 없다").isTrue();
        return matcher.group(1);
    }

    /** 커서를 끝까지 따라가며 "종류:신고id" 를 순서대로 모은다. */
    private List<String> pageThrough(int size) throws Exception {
        List<String> collected = new java.util.ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 20; guard++) {
            MockHttpServletRequestBuilder request = get(BASE).param("size", String.valueOf(size));
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }
            String body = mockMvc.perform(bearer(request, adminToken))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            java.util.regex.Matcher rows = java.util.regex.Pattern.compile(
                            "\"targetType\":\"([A-Z]+)\",\"reportId\":(\\d+)")
                    .matcher(body);
            while (rows.find()) {
                collected.add(rows.group(1) + ":" + rows.group(2));
            }
            if (!body.contains("\"hasNext\":true")) {
                return collected;
            }
            java.util.regex.Matcher next = java.util.regex.Pattern.compile("\"nextCursor\":\"([^\"]+)\"")
                    .matcher(body);
            assertThat(next.find()).as("hasNext 인데 nextCursor 가 없다").isTrue();
            cursor = next.group(1);
        }
        throw new AssertionError("커서가 끝나지 않는다");
    }

    // --- 시드 ---

    private long createMember(String nickname, UserRole role) {
        long userId = Objects.requireNonNull(tx.execute(txStatus -> {
            User user = User.Companion.preRegister();
            em.persist(user);
            em.flush();
            user.completeOnboarding(nickname);
            em.flush();
            return user.getId();
        }));
        tx.executeWithoutResult(txStatus -> em.createQuery("update User u set u.role = :role where u.id = :id")
                .setParameter("role", role)
                .setParameter("id", userId)
                .executeUpdate());
        return userId;
    }

    private long persistReview(long orderItemId, String content) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            Review review = Review.Companion.write(productId, "5kg / 1개", orderItemId, authorId, 2, content, List.of());
            em.persist(review);
            em.flush();
            return review.getId();
        }));
    }

    private long persistReviewBy(long author, long orderItemId, String content) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            Review review = Review.Companion.write(productId, "5kg / 1개", orderItemId, author, 2, content, List.of());
            em.persist(review);
            em.flush();
            return review.getId();
        }));
    }

    /** 접수 시각을 직접 맞춘다 — 엔티티가 생성 시각을 스스로 채워 심는 쪽에서 지정할 수 없다. */
    private void setReportedAt(String table, long reportId, LocalDateTime reportedAt) {
        tx.executeWithoutResult(
                txStatus -> em.createNativeQuery("update " + table + " set created_at = :at where id = :id")
                        .setParameter("at", reportedAt)
                        .setParameter("id", reportId)
                        .executeUpdate());
    }

    private long persistProductReport(ProductReportReason reasonCode, String reasonText) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            Product product = em.find(Product.class, productId);
            ProductReport report = ProductReport.Companion.report(product, createUser(), reasonCode, reasonText);
            em.persist(report);
            em.flush();
            return report.getId();
        }));
    }

    private long persistReviewReport(long reviewId, ReviewReportReason reasonCode, String reasonText) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            Review review = em.find(Review.class, reviewId);
            ReviewReport report = ReviewReport.Companion.report(review, createUser(), reasonCode, reasonText);
            em.persist(report);
            em.flush();
            return report.getId();
        }));
    }

    private long persistUserReport(long targetUserId, ReportReasonCode reasonCode, String reasonText) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            User reporter = em.find(User.class, createUser());
            User target = em.find(User.class, targetUserId);
            UserReport report = UserReport.Companion.report(reporter, target, reasonCode, reasonText);
            em.persist(report);
            em.flush();
            return report.getId();
        }));
    }

    // --- 검증 ---

    private List<ReportDecision> decisions() {
        return tx.execute(
                txStatus -> em.createQuery("select d from ReportDecision d order by d.id asc", ReportDecision.class)
                        .getResultList());
    }

    private ReviewStatus reviewStatusOf(long reviewId) {
        return tx.execute(
                txStatus -> em.createQuery("select r.reviewStatus from Review r where r.id = :id", ReviewStatus.class)
                        .setParameter("id", reviewId)
                        .getSingleResult());
    }

    private InspectionStatus inspectionStatusOf(long productId) {
        return tx.execute(txStatus -> em.createQuery(
                        "select p.inspectionStatus from Product p where p.id = :id", InspectionStatus.class)
                .setParameter("id", productId)
                .getSingleResult());
    }

    private UserStatus statusOf(long userId) {
        return tx.execute(txStatus -> em.createQuery("select u.status from User u where u.id = :id", UserStatus.class)
                .setParameter("id", userId)
                .getSingleResult());
    }

    private List<UserSanction> sanctionsOf(long userId) {
        return tx.execute(txStatus -> em.createQuery(
                        "select s from UserSanction s where s.targetUser.id = :id order by s.id desc",
                        UserSanction.class)
                .setParameter("id", userId)
                .getResultList());
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
