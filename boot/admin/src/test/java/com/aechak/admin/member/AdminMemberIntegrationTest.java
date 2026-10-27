package com.aechak.admin.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.user.sanction.UserSanction;
import com.aechak.domain.user.sanction.enums.SanctionAction;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 어드민 회원 관리 API 통합 테스트 — 권한 경계, 목록 필터·검색·커서, 상태 전이의 금지 조합을 HTTP로 검증한다.
 * 거부된 요청은 상태도 이력도 건드리지 않는지까지 본다 — 반쪽 적용이 남으면 분쟁에서 설명할 수 없다.
 */
class AdminMemberIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/members";

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
        actorId = createMember("운영자", UserRole.ADMIN);
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
        mockMvc.perform(bearer(get(BASE), mintAccessToken(createMember("일반", UserRole.GENERAL), UserRole.GENERAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }

    @Test
    void 일반_GENERAL_토큰은_제재도_403_20011_로_막히고_상태가_바뀌지_않는다() throws Exception {
        long targetId = createMember("대상", UserRole.GENERAL);

        mockMvc.perform(bearer(post(BASE + "/" + targetId + "/sanctions"), mintAccessToken(targetId, UserRole.GENERAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sanctionBody("RESTRICT", "ABUSIVE_REVIEW", null, null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionsOf(targetId)).isEmpty();
    }

    // --- 목록 ---

    @Test
    void 목록은_일반_회원만_가입_역순으로_주고_관리자는_빼고_전체_수를_함께_준다() throws Exception {
        long first = createMember("첫회원", UserRole.GENERAL);
        long second = createMember("둘째회원", UserRole.GENERAL);

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].userId").value(second))
                .andExpect(jsonPath("$.data.items[0].nickname").value("둘째회원"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].joinedAt").exists())
                .andExpect(jsonPath("$.data.items[0].publicId").exists())
                .andExpect(jsonPath("$.data.items[1].userId").value(first))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void 상태_필터는_그_상태의_회원만_준다() throws Exception {
        long suspended = createMember("정지회원", UserRole.GENERAL);
        setStatus(suspended, UserStatus.SUSPENDED, LocalDateTime.now().plusDays(3));
        createMember("정상회원", UserRole.GENERAL);

        mockMvc.perform(bearer(get(BASE).param("status", "SUSPENDED"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(suspended))
                .andExpect(jsonPath("$.data.items[0].sanctionUntil").exists());
    }

    @Test
    void NORMAL_필터는_제재가_없는_계정만_주고_온보딩_전_계정도_포함한다() throws Exception {
        long active = createMember("정상회원", UserRole.GENERAL);
        long pending = createUser(UserStatus.PENDING_ONBOARDING, UserRole.GENERAL);
        long restricted = createMember("제한회원", UserRole.GENERAL);
        setStatus(restricted, UserStatus.RESTRICTED, null);

        mockMvc.perform(bearer(get(BASE).param("status", "NORMAL"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[0].userId").value(pending))
                .andExpect(jsonPath("$.data.items[0].nickname").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].userId").value(active));
    }

    @Test
    void 알_수_없는_상태_필터는_400이_아니라_전체_목록을_준다() throws Exception {
        createMember("정상회원", UserRole.GENERAL);

        mockMvc.perform(bearer(get(BASE).param("status", "오타").param("size", "999"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    void 검색어는_닉네임_부분일치와_회원_ID_완전일치_둘_다_찾는다() throws Exception {
        long coco = createMember("코코집사", UserRole.GENERAL);
        long other = createMember("멍멍이집", UserRole.GENERAL);

        mockMvc.perform(bearer(get(BASE).param("keyword", "코코"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(coco));

        mockMvc.perform(bearer(get(BASE).param("keyword", String.valueOf(other)), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(other));
    }

    @Test
    void 가입일_상한은_그날_가입자를_빼지_않는다() throws Exception {
        long todayMember = createMember("오늘가입", UserRole.GENERAL);
        String today = LocalDateTime.now().toLocalDate().toString();

        mockMvc.perform(bearer(get(BASE).param("joinedFrom", today).param("joinedTo", today), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(todayMember));
    }

    @Test
    void 가입일_범위_밖의_회원은_빠진다() throws Exception {
        createMember("오늘가입", UserRole.GENERAL);
        String yesterday = LocalDateTime.now().minusDays(1).toLocalDate().toString();

        mockMvc.perform(bearer(get(BASE).param("joinedTo", yesterday), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 커서로_이어_받으면_중복도_누락도_없다() throws Exception {
        long first = createMember("1번", UserRole.GENERAL);
        long second = createMember("2번", UserRole.GENERAL);
        long third = createMember("3번", UserRole.GENERAL);

        String cursor = mockMvc.perform(bearer(get(BASE).param("size", "2"), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].userId").value(third))
                .andExpect(jsonPath("$.data.items[1].userId").value(second))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(bearer(get(BASE).param("size", "2").param("cursor", cursor), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(first))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 상태_필터를_바꾼_옛_커서는_400_90002_로_끊는다() throws Exception {
        createMember("1번", UserRole.GENERAL);
        createMember("2번", UserRole.GENERAL);

        String cursor = mockMvc.perform(bearer(get(BASE).param("size", "1"), actorToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(bearer(
                        get(BASE)
                                .param("size", "1")
                                .param("status", "SUSPENDED")
                                .param("cursor", cursor),
                        actorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90002));
    }

    @Test
    void 망가진_커서는_400_90002_를_반환한다() throws Exception {
        mockMvc.perform(bearer(get(BASE).param("cursor", "!!broken!!"), actorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90002));
    }

    @Test
    void 기간이_지난_임시_정지는_유효_상태가_ACTIVE고_저장값은_SUSPENDED로_남는다() throws Exception {
        long expired = createMember("만료정지", UserRole.GENERAL);
        setStatus(expired, UserStatus.SUSPENDED, LocalDateTime.now().minusMinutes(1));

        mockMvc.perform(bearer(get(BASE), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").value(expired))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].storedStatus").value("SUSPENDED"));

        assertThat(statusOf(expired)).isEqualTo(UserStatus.SUSPENDED);
    }

    // --- 상세 ---

    @Test
    void 상세는_가입일_상태_제재_이력을_준다() throws Exception {
        long targetId = createMember("제재회원", UserRole.GENERAL);
        sanction(targetId, "RESTRICT", "ABUSIVE_REVIEW", null, null, "1차 조치").andExpect(status().isNoContent());

        mockMvc.perform(bearer(get(BASE + "/" + targetId), actorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.userId").value(targetId))
                .andExpect(jsonPath("$.data.member.status").value("RESTRICTED"))
                .andExpect(jsonPath("$.data.member.joinedAt").exists())
                .andExpect(jsonPath("$.data.sanctions.length()").value(1))
                .andExpect(jsonPath("$.data.sanctions[0].action").value("RESTRICT"))
                .andExpect(jsonPath("$.data.sanctions[0].reasonCode").value("ABUSIVE_REVIEW"))
                .andExpect(jsonPath("$.data.sanctions[0].memo").value("1차 조치"))
                .andExpect(jsonPath("$.data.sanctions[0].actedByAdminId").value(actorId))
                .andExpect(jsonPath("$.data.sanctions[0].actedAt").exists());
    }

    @Test
    void 없는_회원_상세는_404_30000_를_반환한다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/999999"), actorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(30000));
    }

    @Test
    void 관리자_계정은_이_목록의_대상이_아니라_상세도_404_30000_이다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/" + actorId), actorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(30000));
    }

    // --- 제재 ---

    @Test
    void 이용_제한은_204이고_상태와_이력이_함께_남는다() throws Exception {
        long targetId = createMember("제한대상", UserRole.GENERAL);

        sanction(targetId, "RESTRICT", "ABUSIVE_REVIEW", null, null, "메모").andExpect(status().isNoContent());

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.RESTRICTED);
        assertThat(sanctionUntilOf(targetId)).isNull();
        List<UserSanction> history = sanctionsOf(targetId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getAction()).isEqualTo(SanctionAction.RESTRICT);
        assertThat(history.get(0).getActedByAdminId()).isEqualTo(actorId);
        assertThat(history.get(0).getEffectiveUntil()).isNull();
    }

    @Test
    void 임시_정지는_기간만큼_만료_시각을_세팅하고_이력에도_같은_시각을_남긴다() throws Exception {
        long targetId = createMember("정지대상", UserRole.GENERAL);
        LocalDateTime before = LocalDateTime.now();

        sanction(targetId, "SUSPEND", "CHAT_DISPUTE", 7, null, null).andExpect(status().isNoContent());

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.SUSPENDED);
        LocalDateTime until = sanctionUntilOf(targetId);
        assertThat(until)
                .isAfter(before.plusDays(7).minusMinutes(1))
                .isBefore(before.plusDays(7).plusMinutes(1));
        assertThat(sanctionsOf(targetId).get(0).getEffectiveUntil()).isEqualTo(until);
    }

    @Test
    void 기간_없는_임시_정지는_400_30801_이고_상태도_이력도_남지_않는다() throws Exception {
        long targetId = createMember("정지대상", UserRole.GENERAL);

        sanction(targetId, "SUSPEND", "CHAT_DISPUTE", null, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(30801));

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionsOf(targetId)).isEmpty();
    }

    @Test
    void 임시_정지가_아닌_제재에_기간을_붙이면_400_30802_다() throws Exception {
        long targetId = createMember("제한대상", UserRole.GENERAL);

        sanction(targetId, "RESTRICT", "FALSE_REPORT", 7, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(30802));

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionsOf(targetId)).isEmpty();
    }

    @Test
    void 기타_사유에_상세가_없으면_400_30800_이고_아무것도_바뀌지_않는다() throws Exception {
        long targetId = createMember("제한대상", UserRole.GENERAL);

        sanction(targetId, "RESTRICT", "OTHER", null, "   ", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(30800));

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionsOf(targetId)).isEmpty();
    }

    @Test
    void 영구_정지된_계정은_추가_제재도_해제도_받지_않는다() throws Exception {
        long targetId = createMember("영구정지", UserRole.GENERAL);
        sanction(targetId, "BAN", "FALSE_REPORT", null, null, null).andExpect(status().isNoContent());
        assertThat(statusOf(targetId)).isEqualTo(UserStatus.BANNED);

        sanction(targetId, "RESTRICT", "ABUSIVE_REVIEW", null, null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30018));
        release(targetId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30019));

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.BANNED);
        assertThat(sanctionsOf(targetId)).hasSize(1);
    }

    @Test
    void 탈퇴_처리는_탈퇴_상태로_가고_닉네임_점유를_푼다() throws Exception {
        long targetId = createMember("탈퇴대상", UserRole.GENERAL);

        sanction(targetId, "FORCE_WITHDRAW", "FALSE_REPORT", null, null, null).andExpect(status().isNoContent());

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(nicknameOf(targetId)).isNull();
        assertThat(sanctionsOf(targetId).get(0).getAction()).isEqualTo(SanctionAction.FORCE_WITHDRAW);
    }

    @Test
    void 영구_정지는_탈퇴와_달리_닉네임_점유를_유지한다() throws Exception {
        long targetId = createMember("영구정지", UserRole.GENERAL);

        sanction(targetId, "BAN", "FALSE_REPORT", null, null, null).andExpect(status().isNoContent());

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.BANNED);
        assertThat(nicknameOf(targetId)).isEqualTo("영구정지");
    }

    @Test
    void 탈퇴한_계정은_제재_대상이_아니라_409_30003_이다() throws Exception {
        long targetId = createMember("탈퇴회원", UserRole.GENERAL);
        sanction(targetId, "FORCE_WITHDRAW", "FALSE_REPORT", null, null, null).andExpect(status().isNoContent());

        sanction(targetId, "BAN", "FALSE_REPORT", null, null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30003));

        assertThat(sanctionsOf(targetId)).hasSize(1);
    }

    @Test
    void 자기_자신_제재는_403_30020_다() throws Exception {
        sanction(actorId, "RESTRICT", "ABUSIVE_REVIEW", null, null, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(30020));

        assertThat(statusOf(actorId)).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 관리자_계정_제재는_409_30021_으로_막고_강등을_먼저_하게_한다() throws Exception {
        long otherAdminId = createMember("부운영자", UserRole.ADMIN);

        sanction(otherAdminId, "BAN", "FALSE_REPORT", null, null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30021));

        assertThat(statusOf(otherAdminId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionsOf(otherAdminId)).isEmpty();
    }

    @Test
    void 해제를_제재_경로로_보내면_400_90001_이다() throws Exception {
        long targetId = createMember("제한회원", UserRole.GENERAL);
        setStatus(targetId, UserStatus.RESTRICTED, null);

        sanction(targetId, "RELEASE", "ABUSIVE_REVIEW", null, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.RESTRICTED);
        assertThat(sanctionsOf(targetId)).isEmpty();
    }

    @Test
    void action이나_사유가_빠진_제재_요청은_400_90001_이다() throws Exception {
        long targetId = createMember("대상", UserRole.GENERAL);

        mockMvc.perform(bearer(post(BASE + "/" + targetId + "/sanctions"), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));

        sanction(targetId, "RESTRICT", null, null, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 상한을_넘는_메모는_400_90001_로_걸린다() throws Exception {
        long targetId = createMember("대상", UserRole.GENERAL);

        sanction(targetId, "RESTRICT", "ABUSIVE_REVIEW", null, null, "가".repeat(301))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(90001));

        assertThat(sanctionsOf(targetId)).isEmpty();
    }

    @Test
    void 없는_회원_제재는_404_30000_를_반환한다() throws Exception {
        sanction(999999L, "BAN", "FALSE_REPORT", null, null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(30000));
    }

    // --- 해제 ---

    @Test
    void 해제는_204이고_상태를_되돌리며_해제_이력을_남긴다() throws Exception {
        long targetId = createMember("정지회원", UserRole.GENERAL);
        sanction(targetId, "SUSPEND", "CHAT_DISPUTE", 7, null, null).andExpect(status().isNoContent());

        mockMvc.perform(bearer(post(BASE + "/" + targetId + "/sanctions/release"), actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonDetail\":\"오인 제재 확인\",\"memo\":\"CS 1024\"}"))
                .andExpect(status().isNoContent());

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionUntilOf(targetId)).isNull();
        List<UserSanction> history = sanctionsOf(targetId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getAction()).isEqualTo(SanctionAction.RELEASE);
        assertThat(history.get(0).getReasonCode()).isNull();
        assertThat(history.get(0).getReasonDetail()).isEqualTo("오인 제재 확인");
        assertThat(history.get(0).getActedByAdminId()).isEqualTo(actorId);
    }

    @Test
    void 제재가_없는_회원_해제는_409_30019_이고_이력이_남지_않는다() throws Exception {
        long targetId = createMember("정상회원", UserRole.GENERAL);

        release(targetId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30019));

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionsOf(targetId)).isEmpty();
    }

    @Test
    void 기간이_지난_임시_정지도_해제해_저장값을_정리할_수_있다() throws Exception {
        long targetId = createMember("만료정지", UserRole.GENERAL);
        setStatus(targetId, UserStatus.SUSPENDED, LocalDateTime.now().minusDays(1));

        release(targetId).andExpect(status().isNoContent());

        assertThat(statusOf(targetId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(sanctionUntilOf(targetId)).isNull();
    }

    @Test
    void 해제도_자기_자신과_관리자에게는_막힌다() throws Exception {
        long otherAdminId = createMember("부운영자", UserRole.ADMIN);

        release(actorId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(30020));
        release(otherAdminId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(30021));
    }

    // --- 보조 ---

    private org.springframework.test.web.servlet.ResultActions sanction(
            long targetUserId, String action, String reasonCode, Integer durationDays, String reasonDetail, String memo)
            throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + targetUserId + "/sanctions"), actorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanctionBody(action, reasonCode, durationDays, reasonDetail, memo)));
    }

    private org.springframework.test.web.servlet.ResultActions release(long targetUserId) throws Exception {
        return mockMvc.perform(bearer(post(BASE + "/" + targetUserId + "/sanctions/release"), actorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    private String sanctionBody(
            String action, String reasonCode, Integer durationDays, String reasonDetail, String memo) {
        return "{\"action\":" + quoted(action) + ",\"reasonCode\":" + quoted(reasonCode) + ",\"durationDays\":"
                + (durationDays == null ? "null" : durationDays) + ",\"reasonDetail\":" + quoted(reasonDetail)
                + ",\"memo\":" + quoted(memo) + "}";
    }

    private String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** 온보딩까지 끝낸(프로필·닉네임을 가진) 계정을 심고 역할을 지정한다. */
    private long createMember(String nickname, UserRole role) {
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

    private void setStatus(long userId, UserStatus status, LocalDateTime sanctionUntil) {
        tx.executeWithoutResult(txStatus -> em.createQuery(
                        "update User u set u.status = :st, u.sanctionUntil = :until where u.id = :id")
                .setParameter("st", status)
                .setParameter("until", sanctionUntil)
                .setParameter("id", userId)
                .executeUpdate());
    }

    private UserStatus statusOf(long userId) {
        return tx.execute(txStatus -> em.createQuery("select u.status from User u where u.id = :id", UserStatus.class)
                .setParameter("id", userId)
                .getSingleResult());
    }

    private LocalDateTime sanctionUntilOf(long userId) {
        return tx.execute(
                txStatus -> em.createQuery("select u.sanctionUntil from User u where u.id = :id", LocalDateTime.class)
                        .setParameter("id", userId)
                        .getSingleResult());
    }

    private String nicknameOf(long userId) {
        return tx.execute(
                txStatus -> em.createQuery("select p.nickname from UserProfile p where p.userId = :id", String.class)
                        .setParameter("id", userId)
                        .getResultStream()
                        .findFirst()
                        .orElse(null));
    }

    /** 최신순 — 첫 원소가 가장 최근 조치다. */
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
