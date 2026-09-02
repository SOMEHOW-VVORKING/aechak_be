package com.aechak.admin.seller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.application.pii.port.PiiCrypto;
import com.aechak.domain.seller.application.ApplicationDocument;
import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.BusinessType;
import com.aechak.domain.seller.application.enums.DocumentType;
import com.aechak.domain.user.user.enums.UserRole;
import java.util.Base64;
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

/**
 * 어드민 신청 목록·상세 통합 테스트.
 * 신청자측 API는 api 모듈 소유라 신청서는 도메인·리포지토리로 직접 시딩한다.
 */
class AdminSellerApplicationIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/seller-applications";
    private static final String ACCOUNT_NUMBER = "110123456789";
    private static final String BUSINESS_REG_NO = "1208147521";
    private static final String DOCUMENT_KEY = "sellers/documents/01TEST.png";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    private PiiCrypto piiCrypto;

    private MockMvc mockMvc;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        adminToken = mintAccessToken(createUser());
    }

    @Test
    void 목록을_status로_거르고_제출일_내림차순으로_준다() throws Exception {
        long first = seedApplication(createUser(), true);
        long second = seedApplication(createUser(), true);
        seedApplication(createUser(), false); // DRAFT — 필터 밖

        mockMvc
                .perform(bearer(get(BASE).param("status", "SUBMITTED"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[0].applicationId").value(second))
                .andExpect(jsonPath("$.data.items[1].applicationId").value(first));
    }

    @Test
    void 목록은_status_미지정_시_전체를_주고_페이징한다() throws Exception {
        for (int i = 0; i < 3; i++) {
            seedApplication(createUser(), true);
        }

        mockMvc
                .perform(bearer(get(BASE).param("page", "1").param("size", "2"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void 상세는_계좌_전체_서류_다운로드_URL_동일_사업자번호_이력을_준다() throws Exception {
        long previousId = seedApplication(createUser(), true, "서류 재제출 요망");
        long applicationId = seedApplication(createUser(), true);

        mockMvc
                .perform(bearer(get(BASE + "/" + applicationId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.data.documents[0].documentType").value("ID_CARD"))
                .andExpect(jsonPath("$.data.documents[0].downloadUrl").value("https://fake-download.local/" + DOCUMENT_KEY))
                .andExpect(jsonPath("$.data.reviews.length()").value(0))
                .andExpect(jsonPath("$.data.previousApplications.length()").value(1))
                .andExpect(jsonPath("$.data.previousApplications[0].applicationId").value(previousId));
    }

    @Test
    void 반려된_신청_상세엔_심사_이력과_반려_사유가_실린다() throws Exception {
        long applicationId = seedApplication(createUser(), true, "통장사본 불일치");

        mockMvc
                .perform(bearer(get(BASE + "/" + applicationId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("통장사본 불일치"))
                .andExpect(jsonPath("$.data.reviews.length()").value(1))
                .andExpect(jsonPath("$.data.reviews[0].decision").value("REJECTED"));
    }

    @Test
    void 없는_신청_상세는_404_10100_를_반환한다() throws Exception {
        mockMvc
                .perform(bearer(get(BASE + "/999999"), adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(10100));
    }

    @Test
    void 일반_유저_토큰은_목록_접근이_403_20011_으로_막힌다() throws Exception {
        mockMvc
                .perform(bearer(get(BASE), mintAccessToken(createUser(), UserRole.GENERAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }

    private long seedApplication(long userId, boolean submitted) {
        return seedApplication(userId, submitted, null);
    }

    /** 개인사업자 신청 1건 시딩 — 같은 사업자번호를 공유해 이력 대조 시나리오까지 겸한다. */
    private long seedApplication(long userId, boolean submitted, String rejectedWith) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            SellerApplication application = SellerApplication.Companion.draft(userId, BusinessType.SOLE_PROPRIETORSHIP);
            application.updateDraft(
                    BusinessType.SOLE_PROPRIETORSHIP,
                    "애착상회",
                    BUSINESS_REG_NO,
                    null,
                    "홍길동",
                    "2026-서울강남-0001",
                    "004",
                    Base64.getEncoder().encodeToString(piiCrypto.encrypt(ACCOUNT_NUMBER)),
                    "홍길동");
            application.registerDocument(ApplicationDocument.Companion.of(DocumentType.ID_CARD, DOCUMENT_KEY, "image/png"));
            em.persist(application);
            if (submitted) {
                application.submit();
            }
            if (rejectedWith != null) {
                application.reject(1L, rejectedWith);
            }
            em.flush();
            return application.getId();
        }));
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
