package com.aechak.admin.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.notice.notice.Notice;
import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;
import com.aechak.domain.notice.notice.enums.NoticeStatus;
import com.aechak.domain.user.user.enums.UserRole;
import com.jayway.jsonpath.JsonPath;
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
 * 어드민 공지 API 통합 테스트 — 권한 경계, 되돌릴 수 없는 전이의 중복 거절, 목록 필터·커서를 HTTP로 검증한다.
 *
 * <p>거부된 요청이 행을 건드리지 않았는지까지 DB에서 확인한다 — 200/4xx만 보면 반쪽 적용이 숨는다.
 */
class AdminNoticeIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/notices";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;
    private long actorId;
    private String actorToken;

    @BeforeEach
    void setUpMockMvcAndActor() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        actorId = createUser(com.aechak.domain.user.user.enums.UserStatus.ACTIVE, UserRole.ADMIN);
        actorToken = mintAccessToken(actorId);
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
        String generalToken = mintAccessToken(
                createUser(com.aechak.domain.user.user.enums.UserStatus.ACTIVE, UserRole.GENERAL), UserRole.GENERAL);

        mockMvc.perform(bearer(get(BASE), generalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }

    @Test
    void 일반_GENERAL_토큰은_작성도_403_20011_로_막히고_행이_생기지_않는다() throws Exception {
        String generalToken = mintAccessToken(
                createUser(com.aechak.domain.user.user.enums.UserStatus.ACTIVE, UserRole.GENERAL), UserRole.GENERAL);

        mockMvc.perform(bearer(post(BASE), generalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("점검 안내", "본문", "MAINTENANCE", "ALL", false, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));

        assertThat(allNotices()).isEmpty();
    }

    @Test
    void 일반_GENERAL_토큰은_게시도_403_20011_로_막히고_상태가_초안으로_남는다() throws Exception {
        String publicId = createDraft("점검 안내", "본문", "MAINTENANCE", "ALL", false);
        String generalToken = mintAccessToken(
                createUser(com.aechak.domain.user.user.enums.UserStatus.ACTIVE, UserRole.GENERAL), UserRole.GENERAL);

        mockMvc.perform(bearer(post(BASE + "/" + publicId + "/publish"), generalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));

        Notice stored = noticeOf(publicId);
        assertThat(stored.getStatus()).isEqualTo(NoticeStatus.DRAFT);
        assertThat(stored.getPublishedAt()).isNull();
        assertThat(stored.getLastModifiedByAdminId()).isNull();
    }

    // --- 작성 ---

    @Test
    void 작성은_201이고_초안_상태와_작성자가_남는다() throws Exception {
        String response = mockMvc.perform(bearer(post(BASE), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("정산 주기 변경", "매주 화요일로 바뀌어요.", "SETTLEMENT", "SELLER", true, "법무 확인 완료")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.category").value("SETTLEMENT"))
                .andExpect(jsonPath("$.data.audience").value("SELLER"))
                .andExpect(jsonPath("$.data.pinned").value(true))
                .andExpect(jsonPath("$.data.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.data.createdByAdminId").value(actorId))
                .andExpect(jsonPath("$.data.lastModifiedByAdminId").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Notice stored = noticeOf(JsonPath.read(response, "$.data.noticeId"));
        assertThat(stored.getStatus()).isEqualTo(NoticeStatus.DRAFT);
        assertThat(stored.getAudience()).isEqualTo(NoticeAudience.SELLER);
        assertThat(stored.getCategory()).isEqualTo(NoticeCategory.SETTLEMENT);
        assertThat(stored.getPinned()).isTrue();
        assertThat(stored.getCreatedByAdminId()).isEqualTo(actorId);
    }

    /** 내부 메모는 운영자만 보는 값이라 어드민 상세에는 반드시 실려야 한다(읽는 쪽에는 반대로 없어야 한다). */
    @Test
    void 상세는_내부_메모까지_준다() throws Exception {
        String publicId = createDraft("리뷰 가이드 초안", "사진과 함께 쓴 리뷰가 도움이 돼요.", "SERVICE", "BUYER", false, "문구 확정 전");

        mockMvc.perform(bearer(get(BASE + "/" + publicId), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noticeId").value(publicId))
                .andExpect(jsonPath("$.data.content").value("사진과 함께 쓴 리뷰가 도움이 돼요."))
                .andExpect(jsonPath("$.data.internalMemo").value("문구 확정 전"));
    }

    @Test
    void 제목이_빈_작성은_400이고_행이_생기지_않는다() throws Exception {
        mockMvc.perform(bearer(post(BASE), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("   ", "본문", "SERVICE", "ALL", false, null)))
                .andExpect(status().isBadRequest());

        assertThat(allNotices()).isEmpty();
    }

    /** pinned를 boolean으로 받으면 누락이 조용히 false가 된다 — 필수임을 계약으로 못 박는다. */
    @Test
    void 고정_여부가_누락된_작성은_400이고_행이_생기지_않는다() throws Exception {
        mockMvc.perform(
                        bearer(post(BASE), actorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"title": "제목", "content": "본문", "category": "SERVICE", "audience": "ALL"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(allNotices()).isEmpty();
    }

    @Test
    void 없는_공지_상세는_404_150000_를_반환한다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/01JXXXXXXXXXXXXXXXXXXXXXXX"), actorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(150000));
    }

    // --- 수정 ---

    @Test
    void 게시_중_공지를_고쳐도_상태와_게시_시각은_그대로다() throws Exception {
        String publicId = createDraft("원래 제목", "원래 본문", "SERVICE", "ALL", false, null);
        publish(publicId).andExpect(status().isOk());
        java.time.LocalDateTime publishedAt = noticeOf(publicId).getPublishedAt();

        mockMvc.perform(bearer(put(BASE + "/" + publicId), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("고친 제목", "고친 본문", "POLICY", "SELLER", true, "오타 정정")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.title").value("고친 제목"))
                .andExpect(jsonPath("$.data.lastModifiedByAdminId").value(actorId));

        Notice stored = noticeOf(publicId);
        assertThat(stored.getStatus()).isEqualTo(NoticeStatus.PUBLISHED);
        assertThat(stored.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(stored.getTitle()).isEqualTo("고친 제목");
        assertThat(stored.getAudience()).isEqualTo(NoticeAudience.SELLER);
    }

    @Test
    void 본문이_상한을_넘는_수정은_400_150004_이고_저장값이_그대로다() throws Exception {
        String publicId = createDraft("원래 제목", "원래 본문", "SERVICE", "ALL", false, null);

        mockMvc.perform(bearer(put(BASE + "/" + publicId), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("새 제목", "가".repeat(2001), "SERVICE", "ALL", false, null)))
                .andExpect(status().isBadRequest());

        assertThat(noticeOf(publicId).getTitle()).isEqualTo("원래 제목");
        assertThat(noticeOf(publicId).getContent()).isEqualTo("원래 본문");
    }

    // --- 게시·내림 ---

    @Test
    void 게시는_상태와_게시_시각과_수정자를_함께_남긴다() throws Exception {
        String publicId = createDraft("점검 안내", "본문", "MAINTENANCE", "ALL", true, null);

        publish(publicId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").exists())
                .andExpect(jsonPath("$.data.lastModifiedByAdminId").value(actorId));

        Notice stored = noticeOf(publicId);
        assertThat(stored.getStatus()).isEqualTo(NoticeStatus.PUBLISHED);
        assertThat(stored.getPublishedAt()).isNotNull();
        assertThat(stored.getLastModifiedByAdminId()).isEqualTo(actorId);
    }

    @Test
    void 이미_게시된_공지를_다시_게시하면_409_150006_이고_게시_시각이_바뀌지_않는다() throws Exception {
        String publicId = createDraft("점검 안내", "본문", "MAINTENANCE", "ALL", false, null);
        publish(publicId).andExpect(status().isOk());
        java.time.LocalDateTime publishedAt = noticeOf(publicId).getPublishedAt();

        publish(publicId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(150006));

        assertThat(noticeOf(publicId).getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void 초안을_내리면_409_150007_이고_상태가_초안으로_남는다() throws Exception {
        String publicId = createDraft("점검 안내", "본문", "MAINTENANCE", "ALL", false, null);

        mockMvc.perform(bearer(post(BASE + "/" + publicId + "/unpublish"), actorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(150007));

        assertThat(noticeOf(publicId).getStatus()).isEqualTo(NoticeStatus.DRAFT);
    }

    /**
     * 게시 시각은 서버 시계로 찍히므로 첫 게시와 재게시가 같은 마이크로초에 떨어질 수 있다 —
     * 그 상태로 "갱신됐다"를 느슨하게 단언하면 갱신을 지워도 통과한다. 첫 시각을 과거로 밀어 두고
     * 재게시 뒤 그보다 뒤인지를 엄격히 본다.
     */
    @Test
    void 내린_공지는_게시_기록을_남기고_다시_게시하면_시각이_갱신된다() throws Exception {
        String publicId = createDraft("점검 안내", "본문", "MAINTENANCE", "ALL", false, null);
        publish(publicId).andExpect(status().isOk());
        java.time.LocalDateTime firstPublishedAt = java.time.LocalDateTime.now().minusDays(3);
        backdatePublishedAt(publicId, firstPublishedAt);

        mockMvc.perform(bearer(post(BASE + "/" + publicId + "/unpublish"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").exists());

        // 내림이 게시 기록을 지우지 않았는지 먼저 본다 — 지웠다면 아래 비교가 무의미해진다.
        assertThat(noticeOf(publicId).getPublishedAt()).isEqualTo(firstPublishedAt);

        publish(publicId).andExpect(status().isOk());

        Notice stored = noticeOf(publicId);
        assertThat(stored.getStatus()).isEqualTo(NoticeStatus.PUBLISHED);
        assertThat(stored.getPublishedAt()).isAfter(firstPublishedAt);
    }

    // --- 목록 ---

    @Test
    void 목록은_최근_작성순으로_주고_첫_페이지에만_총건수를_싣는다() throws Exception {
        String first = createDraft("먼저 쓴 공지", "본문", "SERVICE", "ALL", false, null);
        String second = createDraft("나중 쓴 공지", "본문", "SERVICE", "ALL", false, null);

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].noticeId").value(second))
                .andExpect(jsonPath("$.data.items[1].noticeId").value(first))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void 상태_대상_분류_필터는_조건에_맞는_공지만_준다() throws Exception {
        String sellerPolicy = createDraft("셀러 정책", "본문", "POLICY", "SELLER", false, null);
        createDraft("구매자 이벤트", "본문", "EVENT", "BUYER", false, null);
        publish(sellerPolicy).andExpect(status().isOk());

        mockMvc.perform(bearer(get(BASE).param("status", "PUBLISHED"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].noticeId").value(sellerPolicy));

        mockMvc.perform(bearer(get(BASE).param("audience", "BUYER"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].audience").value("BUYER"));

        mockMvc.perform(bearer(get(BASE).param("category", "POLICY"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].category").value("POLICY"));
    }

    @Test
    void 제목_검색은_제목에_든_공지만_준다() throws Exception {
        String target = createDraft("결제 시스템 점검 안내", "본문", "MAINTENANCE", "ALL", false, null);
        createDraft("정산 주기 변경", "결제 시스템이라는 말이 본문에만 있다", "SETTLEMENT", "SELLER", false, null);

        mockMvc.perform(bearer(get(BASE).param("keyword", "결제 시스템"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].noticeId").value(target));
    }

    @Test
    void 커서로_이어_읽으면_겹치지_않고_뒤_페이지에는_총건수가_없다() throws Exception {
        String first = createDraft("1번", "본문", "SERVICE", "ALL", false, null);
        String second = createDraft("2번", "본문", "SERVICE", "ALL", false, null);
        String third = createDraft("3번", "본문", "SERVICE", "ALL", false, null);

        String page = mockMvc.perform(bearer(get(BASE).param("size", "2"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].noticeId").value(third))
                .andExpect(jsonPath("$.data.items[1].noticeId").value(second))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String nextCursor = JsonPath.read(page, "$.data.nextCursor");
        mockMvc.perform(bearer(get(BASE).param("size", "2").param("cursor", nextCursor), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].noticeId").value(first))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalCount").doesNotExist());
    }

    @Test
    void 망가진_커서는_400_90002_를_반환한다() throws Exception {
        mockMvc.perform(bearer(get(BASE).param("cursor", "!!broken!!"), actorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90002));
    }

    // --- 헬퍼 ---

    private String createDraft(String title, String content, String category, String audience, boolean pinned)
            throws Exception {
        return createDraft(title, content, category, audience, pinned, null);
    }

    private String createDraft(
            String title, String content, String category, String audience, boolean pinned, String internalMemo)
            throws Exception {
        String response = mockMvc.perform(bearer(post(BASE), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(title, content, category, audience, pinned, internalMemo)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.noticeId");
    }

    private org.springframework.test.web.servlet.ResultActions publish(String publicId) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + publicId + "/publish"), actorToken));
    }

    private String saveBody(
            String title, String content, String category, String audience, boolean pinned, String internalMemo) {
        return """
                {"title": %s, "content": %s, "category": "%s", "audience": "%s", "pinned": %s, "internalMemo": %s}
                """
                .formatted(quote(title), quote(content), category, audience, pinned, quote(internalMemo));
    }

    private String quote(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** 게시 시각을 과거로 밀어 둔다 — 애그리거트는 시각을 받지 않으므로 저장값을 직접 고친다. */
    private void backdatePublishedAt(String publicId, java.time.LocalDateTime at) {
        tx.execute(txStatus -> em.createQuery("update Notice n set n.publishedAt = :at where n.publicId = :publicId")
                .setParameter("at", at)
                .setParameter("publicId", publicId)
                .executeUpdate());
        tx.execute(txStatus -> {
            em.clear();
            return null;
        });
    }

    private Notice noticeOf(String publicId) {
        return Objects.requireNonNull(tx.execute(
                txStatus -> em.createQuery("select n from Notice n where n.publicId = :publicId", Notice.class)
                        .setParameter("publicId", publicId)
                        .getResultStream()
                        .findFirst()
                        .orElse(null)));
    }

    private List<Notice> allNotices() {
        return Objects.requireNonNull(tx.execute(txStatus ->
                em.createQuery("select n from Notice n", Notice.class).getResultList()));
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
