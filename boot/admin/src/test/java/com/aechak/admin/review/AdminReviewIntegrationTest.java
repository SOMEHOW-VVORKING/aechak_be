package com.aechak.admin.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.admin.support.LockWaitProbe;
import com.aechak.application.review.admin.usecase.AdminReviewUseCase;
import com.aechak.application.review.admin.usecase.command.DeleteReviewCommand;
import com.aechak.application.review.admin.usecase.command.UnblindReviewCommand;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.product.category.Category;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.review.error.ReviewErrorCode;
import com.aechak.domain.review.report.ReviewReport;
import com.aechak.domain.review.report.enums.ReviewReportReason;
import com.aechak.domain.review.review.Review;
import com.aechak.domain.review.review.enums.ReviewBlindActor;
import com.aechak.domain.review.review.enums.ReviewBlindReason;
import com.aechak.domain.review.review.enums.ReviewStatus;
import com.aechak.domain.seller.seller.Seller;
import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
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
 * [통합] 운영자 리뷰 관리 API를 고정함.
 *
 * 깨지면 운영자가 문제 리뷰를 찾을 수 없거나, 블라인드해도 구매자 쪽 평점이 그대로 남거나
 * (product_stats 재집계 누락), 삭제·블라인드의 금지 조합이 열려 종결된 리뷰가 되살아남.
 */
class AdminReviewIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/reviews";
    private static final String REVIEWS_TABLE = "reviews";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    private AdminReviewUseCase adminReviewUseCase;

    @Autowired
    private DataSource dataSource;

    private MockMvc mockMvc;
    private String adminToken;
    private long adminId;
    private long authorId;
    private long sellerUserId;
    private long productId;
    private String productPublicId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        adminId = createUser();
        adminToken = mintAccessToken(adminId);
        sellerUserId = createUser();
        authorId = persistAuthor("초코집사");
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

    @Test
    void 관리자가_아니면_목록이_막힌다() throws Exception {
        String generalToken = mintAccessToken(createUser(), UserRole.GENERAL);

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + generalToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자가_아니면_블라인드가_막히고_상태도_바뀌지_않는다() throws Exception {
        long reviewId = persistReview(5, 1L, "좋아요");
        String generalToken = mintAccessToken(createUser(), UserRole.GENERAL);

        mockMvc.perform(post(BASE + "/" + reviewId + "/blind")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + generalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blindBody("ABUSE", null)))
                .andExpect(status().isForbidden());

        assertEquals(ReviewStatus.PUBLIC, statusOf(reviewId), "거절된 요청이 리뷰를 가려서는 안 된다");
    }

    @Test
    void 블라인드되고_삭제된_리뷰까지_목록에_나온다() throws Exception {
        long visible = persistReview(5, 1L, "노출 리뷰");
        long blinded = persistReview(1, 2L, "블라인드 리뷰");
        long deleted = persistReview(2, 3L, "삭제 리뷰");
        blind(blinded, "ABUSE", "욕설");
        deleteReview(deleted, "AD_SPAM", null);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                // 최신순 — 삭제·블라인드된 행도 빠지지 않는다
                .andExpect(jsonPath("$.data.items[0].reviewId").value(deleted))
                .andExpect(jsonPath("$.data.items[0].reviewStatus").value("DELETED"))
                .andExpect(jsonPath("$.data.items[0].blindActor").value("ADMIN"))
                .andExpect(jsonPath("$.data.items[0].blindReasonCode").value("AD_SPAM"))
                .andExpect(jsonPath("$.data.items[1].reviewId").value(blinded))
                .andExpect(jsonPath("$.data.items[1].reviewStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.data.items[1].blindReasonText").value("욕설"))
                .andExpect(jsonPath("$.data.items[2].reviewId").value(visible))
                .andExpect(jsonPath("$.data.items[2].reviewStatus").value("PUBLIC"))
                .andExpect(jsonPath("$.data.items[2].blindActor").doesNotExist());
    }

    @Test
    void 목록이_상품_별점_상태_검색어로_좁혀진다() throws Exception {
        long lowRated = persistReview(1, 1L, "사료 바꾸고 애가 아팠어요");
        persistReview(5, 2L, "아주 만족합니다");
        long blinded = persistReview(3, 3L, "광고 문구만 반복");
        blind(blinded, "AD_SPAM", null);

        mockMvc.perform(bearer(get(BASE).param("rating", "1")))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reviewId").value(lowRated));
        mockMvc.perform(bearer(get(BASE).param("status", "BLOCKED")))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reviewId").value(blinded));
        mockMvc.perform(bearer(get(BASE).param("keyword", "애가 아팠")))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reviewId").value(lowRated));
        // 상품명으로도 같은 행을 찾는다
        mockMvc.perform(bearer(get(BASE).param("keyword", "저알러지")))
                .andExpect(jsonPath("$.data.totalCount").value(3));
        mockMvc.perform(bearer(get(BASE).param("productId", productPublicId)))
                .andExpect(jsonPath("$.data.totalCount").value(3));
        mockMvc.perform(bearer(get(BASE).param("productId", "NOSUCHPRODUCTID0000000000")))
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    void 신고_필터는_신고가_쌓인_행만_남긴다() throws Exception {
        long reported = persistReview(1, 1L, "신고된 리뷰");
        persistReview(5, 2L, "신고 없는 리뷰");
        persistReport(reported, ReviewReportReason.ABUSE, "욕설이 섞여 있어요");

        mockMvc.perform(bearer(get(BASE).param("reported", "true")))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reviewId").value(reported))
                .andExpect(jsonPath("$.data.items[0].reportCount").value(1));
        mockMvc.perform(bearer(get(BASE).param("reported", "false")))
                .andExpect(jsonPath("$.data.totalCount").value(2));
    }

    @Test
    void 커서로_다음_페이지를_이어_받고_조건이_바뀐_커서는_거절한다() throws Exception {
        long first = persistReview(1, 1L, "첫 번째");
        long second = persistReview(2, 2L, "두 번째");

        String body = mockMvc.perform(bearer(get(BASE).param("size", "1")))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reviewId").value(second))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = cursorOf(body);

        mockMvc.perform(bearer(get(BASE).param("size", "1").param("cursor", cursor)))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reviewId").value(first))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalCount").doesNotExist());

        // 같은 커서를 다른 필터에 끼워 넣으면 앞 페이지가 조용히 건너뛰어진다 — 400(90002)으로 거절한다
        mockMvc.perform(bearer(get(BASE).param("size", "1").param("rating", "1").param("cursor", cursor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90002));
    }

    @Test
    void 공백만_있는_검색어는_필터로_보지_않고_커서는_공백_차이를_견딘다() throws Exception {
        long first = persistReview(1, 1L, "첫 번째");
        long second = persistReview(2, 2L, "두 번째");

        mockMvc.perform(bearer(get(BASE).param("keyword", "   ")))
                .andExpect(jsonPath("$.data.totalCount").value(2));

        String body = mockMvc.perform(bearer(get(BASE).param("size", "1").param("keyword", "번째")))
                .andExpect(jsonPath("$.data.items[0].reviewId").value(second))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 같은 검색어의 공백 차이는 같은 필터다 — 지문이 갈려 커서가 거절되면 다음 페이지를 못 받는다
        mockMvc.perform(bearer(
                        get(BASE).param("size", "1").param("keyword", "  번째 ").param("cursor", cursorOf(body))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].reviewId").value(first));
    }

    @Test
    void 상세에_원문과_사진_신고_이력이_함께_나온다() throws Exception {
        long reviewId = persistReview(2, 1L, "포장이 터져서 왔어요");
        insertReviewImage(reviewId, "reviews/a.png", 1);
        insertReviewImage(reviewId, "reviews/b.png", 0);
        persistReport(reviewId, ReviewReportReason.SPAM, "광고 같아요");

        mockMvc.perform(bearer(get(BASE + "/" + reviewId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("포장이 터져서 왔어요"))
                .andExpect(jsonPath("$.data.visibleContent").value("포장이 터져서 왔어요"))
                .andExpect(jsonPath("$.data.productName").value("저알러지 건식사료 5kg"))
                .andExpect(jsonPath("$.data.sellerName").value("보리펫샵"))
                .andExpect(jsonPath("$.data.authorNickname").value("초코집사"))
                .andExpect(jsonPath("$.data.images.length()").value(2))
                .andExpect(jsonPath("$.data.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.images[1].sortOrder").value(1))
                .andExpect(jsonPath("$.data.reports.length()").value(1))
                .andExpect(jsonPath("$.data.reports[0].reasonCode").value("SPAM"))
                .andExpect(jsonPath("$.data.reports[0].status").value("PENDING"));
    }

    @Test
    void 없는_리뷰_상세는_404다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(110000));
    }

    @Test
    void 블라인드하면_집행자와_사유가_남고_평점_집계에서_빠진다() throws Exception {
        long keep = persistReview(5, 1L, "만족합니다");
        long target = persistReview(1, 2L, "악의적 리뷰");
        recomputeViaBlindAndUnblind(keep); // product_stats 행을 만들어 둔다

        assertEquals(2, reviewCountStat(), "블라인드 전에는 두 건이 집계돼 있어야 한다");
        assertEquals(0, new BigDecimal("3.00").compareTo(averageRatingStat()));

        mockMvc.perform(bearer(post(BASE + "/" + target + "/blind"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blindBody("ABUSE", "  대표 이름까지 적어 욕설  ")))
                .andExpect(status().isNoContent());

        assertEquals(ReviewStatus.BLOCKED, statusOf(target));
        assertEquals(ReviewBlindActor.ADMIN, blindActorOf(target));
        assertEquals(ReviewBlindReason.ABUSE, blindReasonOf(target));
        assertEquals("대표 이름까지 적어 욕설", blindReasonTextOf(target));
        assertEquals(adminId, blindedByOf(target), "집행한 운영자가 남아야 한다");
        assertNotNull(blindedAtOf(target));
        assertEquals(1, reviewCountStat(), "블라인드된 리뷰는 비정규화된 평점 집계에서 빠져야 한다");
        assertEquals(0, new BigDecimal("5.00").compareTo(averageRatingStat()));
    }

    @Test
    void 블라인드를_해제하면_다시_집계에_들어온다() throws Exception {
        long target = persistReview(1, 1L, "악의적 리뷰");
        blind(target, "ABUSE", "욕설");
        assertEquals(0, reviewCountStat());

        mockMvc.perform(bearer(post(BASE + "/" + target + "/unblind"))).andExpect(status().isNoContent());

        assertEquals(ReviewStatus.PUBLIC, statusOf(target));
        assertNull(blindActorOf(target), "해제는 처리 기록까지 비운다");
        assertNull(blindReasonOf(target));
        assertNull(blindedAtOf(target));
        assertEquals(1, reviewCountStat(), "해제한 리뷰는 평점 집계로 돌아와야 한다");
    }

    @Test
    void 마스킹된_리뷰의_블라인드를_해제하면_MASKED로_돌아간다() throws Exception {
        long target = persistReview(3, 1L, "민감한 원문");
        maskReview(target, "** 원문");
        blind(target, "ABUSE", null);

        mockMvc.perform(bearer(post(BASE + "/" + target + "/unblind"))).andExpect(status().isNoContent());

        assertEquals(ReviewStatus.MASKED, statusOf(target), "금칙어 마스킹은 블라인드 해제가 되돌릴 대상이 아니다");
        assertEquals(1, reviewCountStat(), "마스킹된 리뷰는 노출 상태라 집계에 들어온다");
    }

    @Test
    void 이미_블라인드된_리뷰를_다시_블라인드하면_409다() throws Exception {
        long target = persistReview(1, 1L, "악의적 리뷰");
        blind(target, "ABUSE", "먼저 걸린 사유");

        mockMvc.perform(bearer(post(BASE + "/" + target + "/blind"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blindBody("AD_SPAM", "나중 사유")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(110020));

        assertEquals(ReviewBlindReason.ABUSE, blindReasonOf(target), "거절된 요청이 먼저 걸린 사유를 덮어써서는 안 된다");
    }

    @Test
    void 블라인드가_아닌_리뷰를_해제하면_409다() throws Exception {
        long target = persistReview(5, 1L, "노출 중인 리뷰");

        mockMvc.perform(bearer(post(BASE + "/" + target + "/unblind")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(110021));

        assertEquals(ReviewStatus.PUBLIC, statusOf(target));
    }

    @Test
    void 삭제하면_집행자가_남고_집계에서_빠지며_되돌릴_수_없다() throws Exception {
        long target = persistReview(1, 1L, "광고성 리뷰");
        blind(target, "ABUSE", "먼저 블라인드");

        mockMvc.perform(bearer(delete(BASE + "/" + target))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blindBody("AD_SPAM", "광고로 판단")))
                .andExpect(status().isNoContent());

        assertEquals(ReviewStatus.DELETED, statusOf(target), "블라인드된 리뷰도 바로 삭제할 수 있다");
        assertEquals(adminId, deletedByOf(target));
        assertEquals(ReviewBlindReason.AD_SPAM, blindReasonOf(target));
        assertNull(blindedAtOf(target), "삭제는 블라인드가 아니라 종결이다");
        assertEquals(0, reviewCountStat());

        // 되돌리는 경로가 없다 — 해제도 재삭제도 열려 있지 않다
        mockMvc.perform(bearer(post(BASE + "/" + target + "/unblind")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(110021));
        mockMvc.perform(bearer(delete(BASE + "/" + target))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blindBody("OTHER", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(110002));
        assertEquals(ReviewStatus.DELETED, statusOf(target));
    }

    @Test
    void 삭제된_리뷰는_블라인드할_수_없다() throws Exception {
        long target = persistReview(1, 1L, "삭제된 리뷰");
        deleteReview(target, "OTHER", null);

        mockMvc.perform(bearer(post(BASE + "/" + target + "/blind"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blindBody("ABUSE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(110002));

        assertEquals(ReviewStatus.DELETED, statusOf(target));
    }

    @Test
    void 사유_코드가_없으면_400이고_리뷰는_그대로다() throws Exception {
        long target = persistReview(1, 1L, "악의적 리뷰");

        mockMvc.perform(bearer(post(BASE + "/" + target + "/blind"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertEquals(ReviewStatus.PUBLIC, statusOf(target));
    }

    @Test
    void 사유_메모가_상한을_넘으면_400이고_리뷰는_그대로다() throws Exception {
        long target = persistReview(1, 1L, "악의적 리뷰");
        String tooLong = "가".repeat(Review.BLIND_REASON_TEXT_MAX + 1);

        mockMvc.perform(bearer(post(BASE + "/" + target + "/blind"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blindBody("OTHER", tooLong)))
                .andExpect(status().isBadRequest());

        assertEquals(ReviewStatus.PUBLIC, statusOf(target));
        assertNull(blindActorOf(target));
    }

    /**
     * 삭제가 커밋되기 전에 들어온 해제 요청이 종결을 되살리지 못하는지 — 전이 대상을 잠가 읽지 않으면
     * 두 요청이 같은 BLOCKED 상태를 읽어 둘 다 성공하고, 나중에 쓰는 해제가 DELETED를 PUBLIC으로 덮어쓴다.
     * 잠그면 해제의 조회 자체가 삭제 커밋까지 기다렸다가 DELETED를 읽고 409로 거절된다.
     *
     * <p>커밋 보류를 고정 시간으로 재지 않는다 — 해제 요청이 실제로 리뷰 행의 잠금을 기다리는 것을
     * DB에서 확인한 뒤 풀어 준다. 시간으로 재면 느린 러너에서 보류가 해제의 잠금 조회보다 먼저 끝나,
     * 잠금이 없어도 해제가 커밋된 DELETED를 읽고 거절되어 테스트가 거짓 통과한다.
     */
    @Test
    void 삭제가_커밋되기_전에_들어온_해제는_종결을_되살리지_못한다() throws Exception {
        long target = persistReview(1, 1L, "악의적 리뷰");
        blind(target, "ABUSE", "욕설");
        CountDownLatch deleteApplied = new CountDownLatch(1);

        // 삭제 트랜잭션은 전이를 반영한 뒤, 해제 요청이 잠금 대기에 들어간 것을 보고 나서 커밋을 푼다
        CompletableFuture<Void> deleting = CompletableFuture.runAsync(() -> tx.executeWithoutResult(txStatus -> {
            adminReviewUseCase.delete(new DeleteReviewCommand(adminId, target, ReviewBlindReason.AD_SPAM, null));
            deleteApplied.countDown();
            LockWaitProbe.awaitLockWaitOn(dataSource, REVIEWS_TABLE);
        }));

        assertTrue(deleteApplied.await(10, TimeUnit.SECONDS), "삭제 전이가 반영되어야 한다");
        BusinessException rejected = assertThrows(
                BusinessException.class,
                () -> adminReviewUseCase.unblind(new UnblindReviewCommand(adminId, target)),
                "삭제가 커밋된 뒤의 해제는 거절되어야 한다");

        deleting.join();
        assertEquals(ReviewErrorCode.REVIEW_NOT_BLINDED, rejected.getErrorCode());
        assertEquals(ReviewStatus.DELETED, statusOf(target), "해제가 종결된 리뷰를 되살려서는 안 된다");
        assertEquals(0, reviewCountStat(), "되살아나지 않았으므로 평점 집계도 그대로여야 한다");
    }

    // --- 고정 장치 ---

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private static String blindBody(String reasonCode, String reasonText) {
        String textField = reasonText == null ? "null" : "\"" + reasonText + "\"";
        return "{\"reasonCode\":\"" + reasonCode + "\",\"reasonText\":" + textField + "}";
    }

    private static String cursorOf(String body) {
        String marker = "\"nextCursor\":\"";
        int start = body.indexOf(marker) + marker.length();
        return body.substring(start, body.indexOf('"', start));
    }

    private void blind(long reviewId, String reasonCode, String reasonText) throws Exception {
        mockMvc.perform(bearer(post(BASE + "/" + reviewId + "/blind"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blindBody(reasonCode, reasonText)))
                .andExpect(status().isNoContent());
    }

    private void deleteReview(long reviewId, String reasonCode, String reasonText) throws Exception {
        mockMvc.perform(bearer(delete(BASE + "/" + reviewId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blindBody(reasonCode, reasonText)))
                .andExpect(status().isNoContent());
    }

    /** product_stats 행이 아직 없는 상태와 있는 상태를 구분하지 않도록 재집계를 한 번 돌려 둔다. */
    private void recomputeViaBlindAndUnblind(long reviewId) throws Exception {
        blind(reviewId, "OTHER", null);
        mockMvc.perform(bearer(post(BASE + "/" + reviewId + "/unblind"))).andExpect(status().isNoContent());
    }

    private long persistAuthor(String nickname) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            User user = User.Companion.preRegister();
            em.persist(user);
            em.flush();
            user.completeOnboarding(nickname);
            em.flush();
            return user.getId();
        }));
    }

    private long persistReview(int rating, long orderItemId, String content) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            Review review =
                    Review.Companion.write(productId, "5kg / 1개", orderItemId, authorId, rating, content, List.of());
            em.persist(review);
            em.flush();
            return review.getId();
        }));
    }

    private void persistReport(long reviewId, ReviewReportReason reasonCode, String reasonText) {
        tx.executeWithoutResult(txStatus -> {
            Review review = em.find(Review.class, reviewId);
            em.persist(ReviewReport.Companion.report(review, createUser(), reasonCode, reasonText));
            em.flush();
        });
    }

    private void insertReviewImage(long reviewId, String storageKey, int sortOrder) {
        tx.executeWithoutResult(txStatus -> em.createNativeQuery(
                        "insert into review_images (review_id, storage_key, sort_order, created_at, updated_at) "
                                + "values (:rid, :key, :ord, now(6), now(6))")
                .setParameter("rid", reviewId)
                .setParameter("key", storageKey)
                .setParameter("ord", sortOrder)
                .executeUpdate());
    }

    private void maskReview(long reviewId, String displayContent) {
        tx.executeWithoutResult(txStatus -> em.createQuery(
                        "update Review r set r.reviewStatus = :st, r.displayContent = :dc where r.id = :id")
                .setParameter("st", ReviewStatus.MASKED)
                .setParameter("dc", displayContent)
                .setParameter("id", reviewId)
                .executeUpdate());
    }

    private <T> T reviewField(long reviewId, String field, Class<T> type) {
        return tx.execute(txStatus -> em.createQuery("select r." + field + " from Review r where r.id = :id", type)
                .setParameter("id", reviewId)
                .getSingleResult());
    }

    private ReviewStatus statusOf(long reviewId) {
        return reviewField(reviewId, "reviewStatus", ReviewStatus.class);
    }

    private ReviewBlindActor blindActorOf(long reviewId) {
        return reviewField(reviewId, "blindActor", ReviewBlindActor.class);
    }

    private ReviewBlindReason blindReasonOf(long reviewId) {
        return reviewField(reviewId, "blindReasonCode", ReviewBlindReason.class);
    }

    private String blindReasonTextOf(long reviewId) {
        return reviewField(reviewId, "blindReasonText", String.class);
    }

    private java.time.LocalDateTime blindedAtOf(long reviewId) {
        return reviewField(reviewId, "blindedAt", java.time.LocalDateTime.class);
    }

    private Long blindedByOf(long reviewId) {
        return reviewField(reviewId, "blindedByAdminId", Long.class);
    }

    private Long deletedByOf(long reviewId) {
        return reviewField(reviewId, "deletedByAdminId", Long.class);
    }

    private int reviewCountStat() {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            List<Integer> rows = em.createQuery(
                            "select s.reviewCount from ProductStats s where s.productId = :id", Integer.class)
                    .setParameter("id", productId)
                    .getResultList();
            assertTrue(rows.size() == 1, "product_stats 행이 있어야 한다");
            return rows.getFirst();
        }));
    }

    private BigDecimal averageRatingStat() {
        return tx.execute(txStatus -> em.createQuery(
                        "select s.averageRating from ProductStats s where s.productId = :id", BigDecimal.class)
                .setParameter("id", productId)
                .getSingleResult());
    }
}
