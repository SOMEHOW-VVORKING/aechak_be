package com.aechak.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.audit.auditlog.AdminAuditLog;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.domain.product.category.Category;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.review.review.Review;
import com.aechak.domain.review.review.enums.ReviewStatus;
import com.aechak.domain.seller.seller.Seller;
import com.aechak.domain.user.user.User;
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
 * [통합] 리뷰 블라인드·해제·삭제가 감사 행을 남기는지.
 *
 * <p>셋 모두 작성자의 글을 운영자가 가리거나 없애는 조치라 전부 기록한다. 커버리지 테스트는
 * "애너테이션을 붙였나"까지만 보므로, 행의 내용(대상·사유·결과)이 맞는지는 이 자리가 고정한다.
 *
 * <p>사유 칸을 함께 단언하는 이유는 그것이 조치의 근거이기 때문이다 — 고정 어휘를 사람이 읽는
 * 문구로 옮겨 싣는데, 그 변환이 빠지면 운영자가 조치 이력에서 열거 이름을 해독해야 한다.
 */
class AdminAuditReviewRecordingIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/reviews";

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private FilterChainProxy adminSecurityChain;

    private MockMvc mockMvc;
    private long adminId;
    private String adminToken;
    private long authorId;
    private long productId;
    private long orderItemSeq;

    @BeforeEach
    void setUpMockMvcAndFixtures() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(new TraceIdFilter(), adminSecurityChain)
                .build();
        adminId = createOnboardedUser("운영자", UserRole.ADMIN);
        adminToken = mintAccessToken(adminId);
        authorId = createOnboardedUser("초코집사", UserRole.GENERAL);
        long sellerUserId = createOnboardedUser("보리펫샵사장", UserRole.GENERAL);
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
        });
    }

    @Test
    void 리뷰_블라인드는_사유와_함께_성공_행을_남긴다() throws Exception {
        long reviewId = seedReview("배송이 너무 느려요 ***@***.com 으로 연락주세요");

        blind(reviewId, "PRIVACY", "전화번호가 본문에 있다").andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getActorUserId()).isEqualTo(adminId);
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.REVIEW_BLINDED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.REVIEW);
        assertThat(row.getTargetId()).isEqualTo(String.valueOf(reviewId));
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(row.getHttpStatus()).isEqualTo(204);
        assertThat(row.getRequestId()).as("X-Trace-Id가 행에 들어오지 않았다").isNotBlank();
        // 고정 어휘가 사람이 읽는 문구로 들어가고 운영자 메모가 뒤에 붙는다
        assertThat(row.getReason()).isEqualTo("개인정보 노출 — 전화번호가 본문에 있다");

        assertThat(reload(reviewId).getReviewStatus()).isEqualTo(ReviewStatus.BLOCKED);
    }

    /** 메모가 비어 오면 사유 칸에 어휘 문구만 남는다 — 열거 이름이 그대로 새는 것을 막는 자리다 */
    @Test
    void 메모_없이_블라인드하면_사유에_어휘_문구만_남는다() throws Exception {
        long reviewId = seedReview("광고 글입니다");

        blind(reviewId, "AD_SPAM", null).andExpect(status().isNoContent());

        assertThat(onlyRow().getReason()).isEqualTo("광고·도배");
    }

    @Test
    void 블라인드_해제는_사유_없는_성공_행을_남긴다() throws Exception {
        long reviewId = seedReview("오해가 있었습니다");
        blind(reviewId, "OTHER", null).andExpect(status().isNoContent());

        mockMvc.perform(bearer(post(BASE + "/" + reviewId + "/unblind"), adminToken))
                .andExpect(status().isNoContent());

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(2);
        AdminAuditLog released = rows.get(1);
        assertThat(released.getAction()).isEqualTo(AdminAuditAction.REVIEW_UNBLINDED);
        assertThat(released.getTargetId()).isEqualTo(String.valueOf(reviewId));
        assertThat(released.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(released.getReason()).as("해제는 사유를 받는 계약이 아니다").isNull();

        assertThat(reload(reviewId).getReviewStatus()).isEqualTo(ReviewStatus.PUBLIC);
    }

    @Test
    void 리뷰_삭제는_사유와_함께_성공_행을_남긴다() throws Exception {
        long reviewId = seedReview("욕설이 담긴 리뷰");

        deleteReview(reviewId, "ABUSE", "반복 신고 누적").andExpect(status().isNoContent());

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.REVIEW_DELETED);
        assertThat(row.getTargetType()).isEqualTo(AdminAuditTargetType.REVIEW);
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.SUCCESS);
        assertThat(row.getReason()).isEqualTo("욕설·비방 — 반복 신고 누적");
    }

    /** 거부당한 시도도 남는다 — 되돌릴 수 없는 조치를 누가 시도했는지가 기록의 요점이다 */
    @Test
    void 이미_블라인드된_리뷰의_재블라인드는_실패_행을_남긴다() throws Exception {
        long reviewId = seedReview("중복 처리 시도");
        blind(reviewId, "OTHER", null).andExpect(status().isNoContent());

        blind(reviewId, "OTHER", null).andExpect(status().isConflict());

        List<AdminAuditLog> rows = auditRows();
        assertThat(rows).hasSize(2);
        AdminAuditLog failure = rows.get(1);
        assertThat(failure.getAction()).isEqualTo(AdminAuditAction.REVIEW_BLINDED);
        assertThat(failure.getTargetId()).isEqualTo(String.valueOf(reviewId));
        assertThat(failure.getResult()).isEqualTo(AdminAuditResult.FAILURE);
        assertThat(failure.getHttpStatus()).isEqualTo(409);
        // 실패 행에도 사유가 남는다 — 조치보다 먼저 올리기 때문이다
        assertThat(failure.getReason()).isEqualTo("기타");
    }

    /** 없는 리뷰를 집은 시도도 요청이 들고 온 식별자와 함께 남는다 */
    @Test
    void 없는_리뷰_삭제_시도도_실패_행으로_남는다() throws Exception {
        deleteReview(999_999L, "OTHER", null).andExpect(status().isNotFound());

        AdminAuditLog row = onlyRow();
        assertThat(row.getAction()).isEqualTo(AdminAuditAction.REVIEW_DELETED);
        assertThat(row.getTargetId()).isEqualTo("999999");
        assertThat(row.getResult()).isEqualTo(AdminAuditResult.FAILURE);
    }

    @Test
    void 리뷰_조회는_행을_남기지_않는다() throws Exception {
        long reviewId = seedReview("평범한 리뷰");

        mockMvc.perform(bearer(get(BASE), adminToken)).andExpect(status().isOk());
        mockMvc.perform(bearer(get(BASE + "/" + reviewId), adminToken)).andExpect(status().isOk());

        assertThat(auditRows()).isEmpty();
    }

    private ResultActions blind(long reviewId, String reasonCode, String reasonText) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + reviewId + "/blind"), adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reasonBody(reasonCode, reasonText)));
    }

    private ResultActions deleteReview(long reviewId, String reasonCode, String reasonText) throws Exception {
        return mockMvc.perform(bearer(delete(BASE + "/" + reviewId), adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reasonBody(reasonCode, reasonText)));
    }

    private static String reasonBody(String reasonCode, String reasonText) {
        String text = reasonText == null ? "null" : "\"" + reasonText + "\"";
        return "{\"reasonCode\": \"" + reasonCode + "\", \"reasonText\": " + text + "}";
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

    /** 작성 경로는 api 모듈 소유라 도메인·EntityManager로 직접 심는다. */
    private long seedReview(String content) {
        long orderItemId = ++orderItemSeq;
        return Objects.requireNonNull(tx.execute(txStatus -> {
            Review review = Review.Companion.write(productId, "5kg / 1개", orderItemId, authorId, 3, content, List.of());
            em.persist(review);
            em.flush();
            return review.getId();
        }));
    }

    private Review reload(long reviewId) {
        return tx.execute(txStatus -> em.createQuery("select r from Review r where r.id = :id", Review.class)
                .setParameter("id", reviewId)
                .getSingleResult());
    }

    private long createOnboardedUser(String nickname, UserRole role) {
        long userId = Objects.requireNonNull(tx.execute(txStatus -> {
            User user = User.Companion.preRegister();
            em.persist(user);
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

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
