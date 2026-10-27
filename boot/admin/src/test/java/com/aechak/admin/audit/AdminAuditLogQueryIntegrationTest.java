package com.aechak.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.audit.auditlog.AdminAuditLog;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.support.Ulid;
import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * [통합] 감사 조회 계약 — 필터·검색·커서·기록 시작 시점·권한 경계.
 *
 * <p>조회가 조용히 틀리는 자리를 고정한다. 필터가 더 좁거나 더 넓게 걸려도 응답은 그럴듯하고,
 * 커서가 조건을 건너뛰면 빠진 구간이 "조치가 없었다"로 읽힌다. 그래서 각 단언은
 * <b>걸러져야 하는 행을 일부러 섞어 놓고</b> 그것이 빠졌는지까지 본다.
 */
class AdminAuditLogQueryIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/audit-logs";

    /** DB를 왕복한 시각은 초가 0이라도 초까지 직렬화된다 — LocalDateTime.toString()은 그때 초를 생략한다 */
    private static final DateTimeFormatter ISO_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String adminToken;
    private long viewerId;
    private long otherAdminId;
    private String viewerPublicId;
    private LocalDateTime base;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        viewerId = createOnboardedAdmin("김운영");
        otherAdminId = createOnboardedAdmin("이심사");
        adminToken = mintAccessToken(viewerId);
        viewerPublicId = publicIdOf(viewerId);
        base = LocalDateTime.of(2026, 10, 26, 12, 0);
    }

    @Test
    void 행이_없으면_빈_목록과_null_기록_시작_시점을_준다() throws Exception {
        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.recordingSince").doesNotExist());
    }

    /**
     * 소급 기록이 불가능하므로 화면은 "조치가 없었다"와 "그때는 아직 기록하지 않았다"를 구별해야 한다.
     * 필터가 걸려 0건이어도 기록 시작 시점은 전체 기준이어야 그 구별이 성립한다.
     */
    @Test
    void 기록_시작_시점은_필터와_무관하게_전체_최소_조치시각이다() throws Exception {
        seedRow(viewerId, AdminAuditAction.ADMIN_PROMOTED, "11", base.minusDays(3), AdminAuditResult.SUCCESS, null);
        seedRow(viewerId, AdminAuditAction.ADMIN_DEMOTED, "12", base, AdminAuditResult.SUCCESS, null);

        mockMvc.perform(bearer(get(BASE).param("action", "SELLER_APPLICATION_APPROVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.recordingSince").value(ISO_SECONDS.format(base.minusDays(3))));
    }

    @Test
    void 운영자_필터는_그_운영자의_행만_남긴다() throws Exception {
        seedRow(viewerId, AdminAuditAction.ADMIN_PROMOTED, "11", base, AdminAuditResult.SUCCESS, null);
        seedRow(otherAdminId, AdminAuditAction.ADMIN_PROMOTED, "12", base, AdminAuditResult.SUCCESS, null);

        mockMvc.perform(bearer(get(BASE).param("actorPublicId", viewerPublicId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].targetId").value("11"))
                .andExpect(jsonPath("$.data.items[0].actorPublicId").value(viewerPublicId))
                .andExpect(jsonPath("$.data.items[0].actorNickname").value("김운영"))
                .andExpect(jsonPath("$.data.items[0].actorRole").value("ADMIN"));
    }

    @Test
    void 대상_종류와_결과_필터를_함께_걸면_둘_다_맞는_행만_남는다() throws Exception {
        String productId = Ulid.INSTANCE.generate();
        seedRow(viewerId, AdminAuditAction.ADMIN_PROMOTED, "11", base, AdminAuditResult.FAILURE, null);
        seedRow(
                viewerId,
                AdminAuditAction.PRODUCT_INSPECTION_APPROVED,
                productId,
                base,
                AdminAuditResult.SUCCESS,
                null);
        seedRow(
                viewerId,
                AdminAuditAction.PRODUCT_INSPECTION_REJECTED,
                Ulid.INSTANCE.generate(),
                base,
                AdminAuditResult.FAILURE,
                null);

        mockMvc.perform(bearer(get(BASE).param("targetType", "PRODUCT").param("result", "SUCCESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].targetId").value(productId))
                .andExpect(jsonPath("$.data.items[0].action").value("PRODUCT_INSPECTION_APPROVED"))
                .andExpect(jsonPath("$.data.items[0].actionLabel").value("상품 검수 승인"))
                .andExpect(jsonPath("$.data.items[0].targetTypeLabel").value("상품"));
    }

    /** 닫힌 구간이다 — 경계의 두 행이 모두 들어와야 한다 */
    @Test
    void 기간_필터는_경계를_포함하고_밖의_행을_뺀다() throws Exception {
        seedRow(viewerId, AdminAuditAction.ADMIN_PROMOTED, "10", base.minusDays(1), AdminAuditResult.SUCCESS, null);
        seedRow(viewerId, AdminAuditAction.ADMIN_PROMOTED, "11", base, AdminAuditResult.SUCCESS, null);
        seedRow(viewerId, AdminAuditAction.ADMIN_PROMOTED, "12", base.plusDays(1), AdminAuditResult.SUCCESS, null);
        seedRow(viewerId, AdminAuditAction.ADMIN_PROMOTED, "13", base.plusDays(2), AdminAuditResult.SUCCESS, null);

        mockMvc.perform(bearer(get(BASE)
                        .param("from", base.toString())
                        .param("to", base.plusDays(1).toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[0].targetId").value("12"))
                .andExpect(jsonPath("$.data.items[1].targetId").value("11"));
    }

    /**
     * 이름·사유·식별자 셋을 <b>서로 다른 값</b>으로 심는다. 한 행에 같은 값을 넣어 두면 OR 분기
     * 하나가 빠져도 테스트가 통과해 세 갈래 중 둘이 검증되지 않는다.
     */
    @Test
    void 검색어는_대상_이름과_사유와_대상_식별자를_각각_부분_일치한다() throws Exception {
        seedRow(viewerId, AdminAuditAction.SELLER_APPLICATION_REJECTED, "31", base, "보리펫샵", "서류 미비");
        seedRow(viewerId, AdminAuditAction.SELLER_APPLICATION_REJECTED, "32", base, "다정상회", "통장 사본 불일치");

        // 대상 이름만 맞는 행
        mockMvc.perform(bearer(get(BASE).param("keyword", "보리펫샵")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].targetId").value("31"));
        // 사유만 맞는 행
        mockMvc.perform(bearer(get(BASE).param("keyword", "통장")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].targetId").value("32"));
        // 대상 식별자만 맞는 행
        mockMvc.perform(bearer(get(BASE).param("keyword", "31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].targetId").value("31"));
    }

    /**
     * 화면이 운영자 입력을 지우면 {@code ?actorPublicId=}로 보낸다. 빈 문자열을 조건으로 그대로
     * 넘기면 {@code publicId = ''}가 되어 늘 0건이고, 운영자에게 그 0건은 "조치가 없었다"로 읽힌다.
     */
    @Test
    void 빈_운영자_필터는_거르지_않는다() throws Exception {
        seedRow(viewerId, AdminAuditAction.ADMIN_PROMOTED, "11", base, AdminAuditResult.SUCCESS, null);
        seedRow(otherAdminId, AdminAuditAction.ADMIN_PROMOTED, "12", base, AdminAuditResult.SUCCESS, null);

        mockMvc.perform(bearer(get(BASE).param("actorPublicId", "").param("keyword", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2));
    }

    /** LIKE 와일드카드가 글자 그대로 취급돼야 한다 — 아니면 `%` 검색이 전체를 돌려준다 */
    @Test
    void 검색어의_와일드카드는_글자_그대로_찾는다() throws Exception {
        seedRow(
                viewerId,
                AdminAuditAction.SELLER_APPLICATION_REJECTED,
                "41",
                base,
                AdminAuditResult.SUCCESS,
                "할인 100% 표기");
        seedRow(viewerId, AdminAuditAction.SELLER_APPLICATION_REJECTED, "42", base, AdminAuditResult.SUCCESS, "서류 미비");

        mockMvc.perform(bearer(get(BASE).param("keyword", "100%")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].targetId").value("41"));
    }

    @Test
    void 커서로_최근순_페이지를_끊김_없이_이어_읽는다() throws Exception {
        for (int i = 1; i <= 5; i++) {
            seedRow(
                    viewerId,
                    AdminAuditAction.ADMIN_PROMOTED,
                    String.valueOf(i),
                    base.plusMinutes(i),
                    AdminAuditResult.SUCCESS,
                    null);
        }

        JsonNode first = perform(get(BASE).param("size", "2"));
        assertThat(targetIds(first)).containsExactly("5", "4");
        assertThat(first.at("/data/totalCount").asLong()).isEqualTo(5L);
        assertThat(first.at("/data/hasNext").asBoolean()).isTrue();

        JsonNode second = perform(get(BASE)
                .param("size", "2")
                .param("cursor", first.at("/data/nextCursor").asText()));
        assertThat(targetIds(second)).containsExactly("3", "2");
        // 두 번째 페이지부터는 총건수·기록 시작 시점을 싣지 않는다
        assertThat(second.at("/data/totalCount").isNull()).isTrue();
        assertThat(second.at("/data/recordingSince").isNull()).isTrue();

        JsonNode third = perform(get(BASE)
                .param("size", "2")
                .param("cursor", second.at("/data/nextCursor").asText()));
        assertThat(targetIds(third)).containsExactly("1");
        assertThat(third.at("/data/hasNext").asBoolean()).isFalse();
        assertThat(third.at("/data/nextCursor").isNull()).isTrue();
    }

    /** 조건을 바꾼 채 이전 커서로 넘어가면 빠진 구간이 생긴다 — 조용히 돌려주지 않고 끊는다 */
    @Test
    void 필터를_바꾼_채_이전_커서를_쓰면_90002로_끊는다() throws Exception {
        seedRow(viewerId, AdminAuditAction.ADMIN_PROMOTED, "11", base, AdminAuditResult.SUCCESS, null);
        seedRow(viewerId, AdminAuditAction.ADMIN_PROMOTED, "12", base, AdminAuditResult.SUCCESS, null);
        String cursor =
                perform(get(BASE).param("size", "1")).at("/data/nextCursor").asText();

        mockMvc.perform(bearer(
                        get(BASE).param("size", "1").param("cursor", cursor).param("result", "FAILURE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90002));
    }

    @Test
    void 뒤집힌_기간과_짝이_안_맞는_조치는_90001로_끊는다() throws Exception {
        mockMvc.perform(bearer(
                        get(BASE).param("from", base.plusDays(1).toString()).param("to", base.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));

        mockMvc.perform(bearer(get(BASE).param("targetType", "PRODUCT").param("action", "ADMIN_PROMOTED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));
    }

    /** 조치한 계정이 지워져도 행은 남아야 한다 — 그게 사라지면 감사 로그가 아니다 */
    @Test
    void 조치한_계정이_없어도_행은_남고_운영자_신원만_빈다() throws Exception {
        seedRow(999_999L, AdminAuditAction.ADMIN_PROMOTED, "11", base, AdminAuditResult.SUCCESS, null);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].targetId").value("11"))
                .andExpect(jsonPath("$.data.items[0].actorPublicId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].actorNickname").doesNotExist());
    }

    @Test
    void 일반_계정_토큰은_403으로_막히고_아무것도_보지_못한다() throws Exception {
        seedRow(viewerId, AdminAuditAction.ADMIN_PROMOTED, "11", base, AdminAuditResult.SUCCESS, null);
        String generalToken = mintAccessToken(createUser(), UserRole.GENERAL);

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + generalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(20004));
    }

    private JsonNode perform(MockHttpServletRequestBuilder builder) throws Exception {
        String body = mockMvc.perform(bearer(builder))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private java.util.List<String> targetIds(JsonNode response) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        response.at("/data/items").forEach(item -> ids.add(item.get("targetId").asText()));
        return ids;
    }

    private void seedRow(
            long actorUserId,
            AdminAuditAction action,
            String targetId,
            LocalDateTime actedAt,
            AdminAuditResult result,
            String labelAndReason) {
        seedRow(actorUserId, action, targetId, actedAt, result, labelAndReason, labelAndReason);
    }

    /** 이름과 사유를 따로 심는다 — 검색의 OR 분기를 각각 확인하려면 둘이 달라야 한다 */
    private void seedRow(
            long actorUserId,
            AdminAuditAction action,
            String targetId,
            LocalDateTime actedAt,
            String targetLabel,
            String reason) {
        seedRow(actorUserId, action, targetId, actedAt, AdminAuditResult.SUCCESS, targetLabel, reason);
    }

    private void seedRow(
            long actorUserId,
            AdminAuditAction action,
            String targetId,
            LocalDateTime actedAt,
            AdminAuditResult result,
            String targetLabel,
            String reason) {
        tx.executeWithoutResult(txStatus -> em.persist(AdminAuditLog.Companion.record(
                actorUserId,
                action,
                action.getTargetType(),
                targetId,
                targetLabel,
                reason,
                result,
                result == AdminAuditResult.SUCCESS ? 204 : 409,
                "req-" + targetId,
                "10.42.8.117",
                actedAt)));
    }

    /**
     * 닉네임이 달린 관리자를 심는다. 베이스의 {@code createUser}는 온보딩 전 계정을 만들어
     * 프로필 행이 없어, 그대로 쓰면 응답의 운영자 이름이 늘 null인 채로 통과한다.
     */
    private long createOnboardedAdmin(String nickname) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            User user = User.Companion.preRegister();
            user.completeOnboarding(nickname);
            em.persist(user);
            em.flush();
            em.createQuery("update User u set u.role = :role where u.id = :id")
                    .setParameter("role", UserRole.ADMIN)
                    .setParameter("id", user.getId())
                    .executeUpdate();
            return user.getId();
        }));
    }

    private String publicIdOf(long userId) {
        return Objects.requireNonNull(
                tx.execute(txStatus -> em.createQuery("select u.publicId from User u where u.id = :id", String.class)
                        .setParameter("id", userId)
                        .getSingleResult()));
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }
}
