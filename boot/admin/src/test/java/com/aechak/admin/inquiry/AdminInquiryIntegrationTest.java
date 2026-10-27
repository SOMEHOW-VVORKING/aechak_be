package com.aechak.admin.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.application.inquiry.usecase.result.AdminInquirySummaryResult;
import com.aechak.domain.inquiry.inquiry.Inquiry;
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;
import com.aechak.domain.inquiry.inquiry.enums.InquiryType;
import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import java.time.LocalDateTime;
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
 * 어드민 서비스 문의 조회·답변 통합 테스트.
 * 쓰기 경로는 응답만 보지 않고 저장된 애그리거트까지 되읽어 부작용이 실제로 일어났는지(또는 안 일어났는지) 확인한다.
 */
class AdminInquiryIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/inquiries";
    private static final String REPLY_EMAIL = "buyer@aechak.test";
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 10, 20, 9, 0, 0);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;
    private long adminId;
    private String adminToken;
    private long buyerId;

    @BeforeEach
    void setUpMockMvcAndActors() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        adminId = createOnboardedUser("운영자", UserRole.ADMIN);
        adminToken = mintAccessToken(adminId);
        buyerId = createOnboardedUser("코코맘", UserRole.GENERAL);
    }

    @Test
    void 토큰_없이_목록을_조회하면_401_20004_를_반환한다() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(20004));
    }

    @Test
    void 일반_GENERAL_토큰은_문의_목록과_답변_경로가_403_20011_로_막힌다() throws Exception {
        long inquiryId = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제가 안 돼요", BASE_TIME);
        String generalToken = mintAccessToken(buyerId, UserRole.GENERAL);

        mockMvc.perform(bearer(get(BASE), generalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
        mockMvc.perform(bearer(answerRequest(inquiryId, "처리했습니다"), generalToken))
                .andExpect(status().isForbidden());

        // 막혔다는 응답만 믿지 않고 답변이 실제로 안 남았는지 확인한다
        assertThat(reload(inquiryId).getAnswer()).isNull();
        assertThat(reload(inquiryId).getStatus()).isEqualTo(InquiryStatus.RECEIVED);
    }

    @Test
    void 목록은_접수_오래된_순으로_주고_첫_페이지에만_총건수를_싣는다() throws Exception {
        long oldest = seedInquiry(buyerId, InquiryType.ACCOUNT, "로그인이 안 돼요", BASE_TIME.minusHours(3));
        long newest = seedInquiry(buyerId, InquiryType.DELIVERY_CLAIM, "송장 조회가 안 돼요", BASE_TIME.minusHours(1));
        long middle = seedInquiry(buyerId, InquiryType.SELLER_PRODUCT, "셀러가 계좌이체를 요구해요", BASE_TIME.minusHours(2));

        mockMvc.perform(bearer(get(BASE), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].inquiryId").value(oldest))
                .andExpect(jsonPath("$.data.items[1].inquiryId").value(middle))
                .andExpect(jsonPath("$.data.items[2].inquiryId").value(newest))
                // 목록도 작성자 표기를 들고 있어야 운영 화면이 문의자를 그린다 — userId만으로는 못 쓴다
                .andExpect(jsonPath("$.data.items[0].author.userId").value(buyerId))
                .andExpect(jsonPath("$.data.items[0].author.nickname").value("코코맘"))
                .andExpect(jsonPath("$.data.items[0].author.withdrawn").value(false));

        String cursor = firstPageCursor(1);
        mockMvc.perform(bearer(get(BASE).param("cursor", cursor).param("size", "1"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].inquiryId").value(middle));
    }

    @Test
    void 커서는_접수_시각이_같은_행도_건너뛰지_않는다() throws Exception {
        long first = seedInquiry(buyerId, InquiryType.SERVICE_ETC, "문의 1", BASE_TIME);
        long second = seedInquiry(buyerId, InquiryType.SERVICE_ETC, "문의 2", BASE_TIME);
        long third = seedInquiry(buyerId, InquiryType.SERVICE_ETC, "문의 3", BASE_TIME);

        mockMvc.perform(bearer(get(BASE).param("size", "2"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items[0].inquiryId").value(first))
                .andExpect(jsonPath("$.data.items[1].inquiryId").value(second));

        mockMvc.perform(bearer(get(BASE).param("cursor", firstPageCursor(2)).param("size", "2"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].inquiryId").value(third))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 목록은_상태와_유형으로_거른다() throws Exception {
        long account = seedInquiry(buyerId, InquiryType.ACCOUNT, "계정 문의", BASE_TIME.minusHours(2));
        long payment = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제 문의", BASE_TIME.minusHours(1));
        mockMvc.perform(bearer(statusRequest(payment, InquiryStatus.IN_PROGRESS), adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(bearer(get(BASE).param("status", "RECEIVED"), adminToken))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].inquiryId").value(account));
        mockMvc.perform(bearer(get(BASE).param("inquiryType", "ORDER_PAYMENT"), adminToken))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].inquiryId").value(payment));
        mockMvc.perform(bearer(get(BASE).param("status", "RECEIVED").param("inquiryType", "ORDER_PAYMENT"), adminToken))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 키워드는_문의_내용과_답변_이메일을_함께_훑는다() throws Exception {
        long bySubject = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "적립금이 사라졌어요", BASE_TIME.minusHours(2));
        long byEmail =
                seedInquiry(buyerId, InquiryType.ACCOUNT, "로그인이 안 돼요", BASE_TIME.minusHours(1), "kokomom@aechak.test");

        mockMvc.perform(bearer(get(BASE).param("keyword", "적립금"), adminToken))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].inquiryId").value(bySubject));
        mockMvc.perform(bearer(get(BASE).param("keyword", "kokomom"), adminToken))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].inquiryId").value(byEmail));
        mockMvc.perform(bearer(get(BASE).param("keyword", "   "), adminToken))
                .andExpect(jsonPath("$.data.totalCount").value(2));
    }

    @Test
    void 목록의_내용_미리보기는_줄바꿈을_접고_상한에서_자른다() throws Exception {
        String content = "앞줄\n\n  뒷줄 " + "가".repeat(AdminInquirySummaryResult.PREVIEW_LENGTH);
        seedInquiry(buyerId, InquiryType.SERVICE_ETC, content, BASE_TIME);

        String expected = ("앞줄 뒷줄 " + "가".repeat(AdminInquirySummaryResult.PREVIEW_LENGTH))
                        .substring(0, AdminInquirySummaryResult.PREVIEW_LENGTH)
                + "…";
        mockMvc.perform(bearer(get(BASE), adminToken))
                .andExpect(jsonPath("$.data.items[0].contentPreview").value(expected));
    }

    @Test
    void 잘못된_커서는_400_90002_를_반환한다() throws Exception {
        mockMvc.perform(bearer(get(BASE).param("cursor", "!!!not-base64!!!"), adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90002));
    }

    @Test
    void 상세는_전문과_작성자_표기와_같은_작성자의_과거_문의를_준다() throws Exception {
        long previous = seedInquiry(buyerId, InquiryType.DELIVERY_CLAIM, "지난 배송 문의", BASE_TIME.minusDays(2));
        seedInquiry(createOnboardedUser("남의계정", UserRole.GENERAL), InquiryType.ACCOUNT, "남의 문의", BASE_TIME);
        long inquiryId = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제는 됐는데 내역이 없어요", BASE_TIME);

        mockMvc.perform(bearer(get(BASE + "/" + inquiryId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("결제는 됐는데 내역이 없어요"))
                .andExpect(jsonPath("$.data.replyEmail").value(REPLY_EMAIL))
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.answer").value(nullValue()))
                .andExpect(jsonPath("$.data.author.userId").value(buyerId))
                .andExpect(jsonPath("$.data.author.nickname").value("코코맘"))
                .andExpect(jsonPath("$.data.author.withdrawn").value(false))
                .andExpect(jsonPath("$.data.previousInquiries.length()").value(1))
                .andExpect(jsonPath("$.data.previousInquiries[0].inquiryId").value(previous));
    }

    @Test
    void 탈퇴한_작성자의_문의는_목록과_상세_모두_익명_표기로_내려온다() throws Exception {
        long withdrawnId = createOnboardedUser("떠난사람", UserRole.GENERAL);
        setStatus(withdrawnId, UserStatus.WITHDRAWN);
        long inquiryId = seedInquiry(withdrawnId, InquiryType.SERVICE_ETC, "탈퇴 전 문의", BASE_TIME);

        mockMvc.perform(bearer(get(BASE + "/" + inquiryId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.withdrawn").value(true))
                .andExpect(jsonPath("$.data.author.nickname").value("탈퇴한 사용자"));
        mockMvc.perform(bearer(get(BASE), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].author.withdrawn").value(true))
                .andExpect(jsonPath("$.data.items[0].author.nickname").value("탈퇴한 사용자"));
    }

    @Test
    void 작성자가_여러_명인_목록도_줄마다_자기_작성자를_받는다() throws Exception {
        long otherId = createOnboardedUser("먼지아빠", UserRole.GENERAL);
        seedInquiry(buyerId, InquiryType.ACCOUNT, "내 문의", BASE_TIME.minusHours(2));
        seedInquiry(otherId, InquiryType.ACCOUNT, "남의 문의", BASE_TIME.minusHours(1));

        mockMvc.perform(bearer(get(BASE), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].author.nickname").value("코코맘"))
                .andExpect(jsonPath("$.data.items[1].author.nickname").value("먼지아빠"));
    }

    @Test
    void 없는_문의_상세는_404_120005_를_반환한다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/999999"), adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(120005));
    }

    @Test
    void 답변을_등록하면_완료로_전이하고_답변자와_완료시각이_남는다() throws Exception {
        long inquiryId = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제 문의", BASE_TIME);

        mockMvc.perform(bearer(answerRequest(inquiryId, "승인 취소를 확인했습니다."), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.answer").value("승인 취소를 확인했습니다."))
                .andExpect(jsonPath("$.data.answeredByAdminId").value(adminId));

        Inquiry saved = reload(inquiryId);
        assertThat(saved.getStatus()).isEqualTo(InquiryStatus.DONE);
        assertThat(saved.getAnswer()).isEqualTo("승인 취소를 확인했습니다.");
        assertThat(saved.getAnsweredByAdminId()).isEqualTo(adminId);
        assertThat(saved.getAssignedAdminId()).isEqualTo(adminId);
        assertThat(saved.getResolvedAt()).isNotNull();
    }

    @Test
    void 이미_답변한_문의에_다시_답변하면_409_120008_이고_기존_답변이_그대로다() throws Exception {
        long inquiryId = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제 문의", BASE_TIME);
        mockMvc.perform(bearer(answerRequest(inquiryId, "첫 답변입니다."), adminToken))
                .andExpect(status().isOk());
        long otherAdminId = createOnboardedUser("다른운영자", UserRole.ADMIN);

        mockMvc.perform(bearer(answerRequest(inquiryId, "덮어쓰는 답변"), mintAccessToken(otherAdminId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(120008));

        Inquiry saved = reload(inquiryId);
        assertThat(saved.getAnswer()).isEqualTo("첫 답변입니다.");
        assertThat(saved.getAnsweredByAdminId()).isEqualTo(adminId);
    }

    @Test
    void 공백_답변은_400_120006_이고_상태가_접수에_머문다() throws Exception {
        long inquiryId = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제 문의", BASE_TIME);

        mockMvc.perform(bearer(answerRequest(inquiryId, "   "), adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(120006));

        Inquiry saved = reload(inquiryId);
        assertThat(saved.getStatus()).isEqualTo(InquiryStatus.RECEIVED);
        assertThat(saved.getAnswer()).isNull();
    }

    @Test
    void 상한을_넘는_답변은_400_120007_이고_상한_길이는_통과한다() throws Exception {
        long overLimitTarget = seedInquiry(buyerId, InquiryType.SERVICE_ETC, "문의 A", BASE_TIME.minusHours(1));
        long atLimitTarget = seedInquiry(buyerId, InquiryType.SERVICE_ETC, "문의 B", BASE_TIME);

        mockMvc.perform(bearer(answerRequest(overLimitTarget, "가".repeat(Inquiry.MAX_ANSWER_LENGTH + 1)), adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(120007));
        mockMvc.perform(bearer(answerRequest(atLimitTarget, "가".repeat(Inquiry.MAX_ANSWER_LENGTH)), adminToken))
                .andExpect(status().isOk());

        assertThat(reload(overLimitTarget).getStatus()).isEqualTo(InquiryStatus.RECEIVED);
        assertThat(reload(atLimitTarget).getStatus()).isEqualTo(InquiryStatus.DONE);
    }

    @Test
    void 답변_필드가_없는_요청은_400_으로_거절된다() throws Exception {
        long inquiryId = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제 문의", BASE_TIME);

        mockMvc.perform(bearer(
                        post(BASE + "/" + inquiryId + "/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"),
                        adminToken))
                .andExpect(status().isBadRequest());

        assertThat(reload(inquiryId).getAnswer()).isNull();
    }

    @Test
    void 상태를_처리중으로_옮기면_담당자가_남고_답변은_생기지_않는다() throws Exception {
        long inquiryId = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제 문의", BASE_TIME);

        mockMvc.perform(bearer(statusRequest(inquiryId, InquiryStatus.IN_PROGRESS), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.assignedAdminId").value(adminId));

        Inquiry saved = reload(inquiryId);
        assertThat(saved.getStatus()).isEqualTo(InquiryStatus.IN_PROGRESS);
        assertThat(saved.getAssignedAdminId()).isEqualTo(adminId);
        assertThat(saved.getAnswer()).isNull();
        assertThat(saved.getAnsweredByAdminId()).isNull();
        assertThat(saved.getResolvedAt()).isNull();
    }

    @Test
    void 보류에서_처리중으로도_옮길_수_있다() throws Exception {
        long inquiryId = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제 문의", BASE_TIME);
        mockMvc.perform(bearer(statusRequest(inquiryId, InquiryStatus.ON_HOLD), adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(bearer(statusRequest(inquiryId, InquiryStatus.IN_PROGRESS), adminToken))
                .andExpect(status().isOk());

        assertThat(reload(inquiryId).getStatus()).isEqualTo(InquiryStatus.IN_PROGRESS);
    }

    @Test
    void 완료와_접수로의_상태_전이는_409_120009_이고_상태가_그대로다() throws Exception {
        long inquiryId = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제 문의", BASE_TIME);

        for (InquiryStatus forbidden : new InquiryStatus[] {InquiryStatus.DONE, InquiryStatus.RECEIVED}) {
            mockMvc.perform(bearer(statusRequest(inquiryId, forbidden), adminToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value(120009));
        }

        Inquiry saved = reload(inquiryId);
        assertThat(saved.getStatus()).isEqualTo(InquiryStatus.RECEIVED);
        assertThat(saved.getAssignedAdminId()).isNull();
    }

    @Test
    void 완료된_문의는_상태_전이로_되돌려지지_않는다() throws Exception {
        long inquiryId = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제 문의", BASE_TIME);
        mockMvc.perform(bearer(answerRequest(inquiryId, "처리했습니다."), adminToken)).andExpect(status().isOk());

        mockMvc.perform(bearer(statusRequest(inquiryId, InquiryStatus.IN_PROGRESS), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(120009));

        assertThat(reload(inquiryId).getStatus()).isEqualTo(InquiryStatus.DONE);
    }

    @Test
    void 같은_상태로의_전이는_409_120009_로_거절된다() throws Exception {
        long inquiryId = seedInquiry(buyerId, InquiryType.ORDER_PAYMENT, "결제 문의", BASE_TIME);
        mockMvc.perform(bearer(statusRequest(inquiryId, InquiryStatus.IN_PROGRESS), adminToken))
                .andExpect(status().isOk());
        long otherAdminId = createOnboardedUser("다른운영자", UserRole.ADMIN);

        mockMvc.perform(bearer(statusRequest(inquiryId, InquiryStatus.IN_PROGRESS), mintAccessToken(otherAdminId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(120009));

        assertThat(reload(inquiryId).getAssignedAdminId()).isEqualTo(adminId);
    }

    @Test
    void 없는_문의의_답변_등록은_404_120005_를_반환한다() throws Exception {
        mockMvc.perform(bearer(answerRequest(999999L, "답변"), adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(120005));
    }

    /** 첫 페이지를 size만큼 받아 nextCursor를 꺼낸다 — 커서 형식을 테스트가 알지 않게 서버가 준 값만 쓴다. */
    private String firstPageCursor(int size) throws Exception {
        String body = mockMvc.perform(bearer(get(BASE).param("size", String.valueOf(size)), adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int at = body.indexOf("\"nextCursor\":\"");
        assertThat(at).isNotEqualTo(-1);
        int from = at + "\"nextCursor\":\"".length();
        return body.substring(from, body.indexOf('"', from));
    }

    private MockHttpServletRequestBuilder answerRequest(long inquiryId, String answer) {
        return post(BASE + "/" + inquiryId + "/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\": \"" + answer + "\"}");
    }

    private MockHttpServletRequestBuilder statusRequest(long inquiryId, InquiryStatus status) {
        return patch(BASE + "/" + inquiryId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status.name() + "\"}");
    }

    private long seedInquiry(long userId, InquiryType type, String content, LocalDateTime receivedAt) {
        return seedInquiry(userId, type, content, receivedAt, REPLY_EMAIL);
    }

    /** 문의 1건 시딩 — 접수 경로는 api 모듈 소유라 도메인·EntityManager로 직접 만들고 접수 시각만 고정한다. */
    private long seedInquiry(
            long userId, InquiryType type, String content, LocalDateTime receivedAt, String replyEmail) {
        long inquiryId = Objects.requireNonNull(tx.execute(txStatus -> {
            Inquiry inquiry = Inquiry.Companion.receive(userId, type, replyEmail, content);
            em.persist(inquiry);
            em.flush();
            return inquiry.getId();
        }));
        // created_at은 BaseEntity가 @PrePersist로 채워 도메인으로는 못 고친다 — 정렬·커서 검증에 쓸 시각을 네이티브로 덮는다
        tx.executeWithoutResult(
                txStatus -> em.createNativeQuery("update admin_inquiries set created_at = ?1 where id = ?2")
                        .setParameter(1, receivedAt)
                        .setParameter(2, inquiryId)
                        .executeUpdate());
        return inquiryId;
    }

    private Inquiry reload(long inquiryId) {
        return tx.execute(txStatus -> em.createQuery("select i from Inquiry i where i.id = :id", Inquiry.class)
                .setParameter("id", inquiryId)
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

    private void setStatus(long userId, UserStatus status) {
        tx.executeWithoutResult(txStatus -> em.createQuery("update User u set u.status = :st where u.id = :id")
                .setParameter("st", status)
                .setParameter("id", userId)
                .executeUpdate());
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
