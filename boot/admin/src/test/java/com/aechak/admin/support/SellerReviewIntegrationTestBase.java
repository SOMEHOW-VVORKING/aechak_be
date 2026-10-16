package com.aechak.admin.support;

import com.aechak.application.pii.port.PiiCrypto;
import com.aechak.domain.seller.application.ApplicationDocument;
import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.BusinessType;
import com.aechak.domain.seller.application.enums.DocumentType;
import java.util.Base64;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 셀러 심사 통합 테스트 공용 베이스 — MockMvc(시큐리티 체인 포함)와 신청서 시딩을 제공한다.
 * 신청자측 API는 api 모듈 소유라 신청서는 도메인·EntityManager로 직접 만든다.
 */
public abstract class SellerReviewIntegrationTestBase extends IntegrationTestBase {

    protected static final String BASE = "/api/v1/admin/seller-applications";
    protected static final String ACCOUNT_NUMBER = "110123456789";
    protected static final String BUSINESS_REG_NO = "1208147521";
    protected static final String DOCUMENT_KEY = "sellers/documents/01TEST.png";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    protected PiiCrypto piiCrypto;

    protected MockMvc mockMvc;
    protected String adminToken;

    @BeforeEach
    void setUpMockMvcAndAdminToken() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        adminToken = mintAccessToken(createUser());
    }

    protected long seedApplication(long userId, boolean submitted) {
        return seedApplication(userId, submitted, null);
    }

    protected long seedApplication(long userId, boolean submitted, String rejectedWith) {
        return seedApplication(
                userId, submitted, rejectedWith, BusinessType.SOLE_PROPRIETORSHIP, "애착상회", BUSINESS_REG_NO);
    }

    /** 신청 1건 시딩 — 기본은 서류·필수 정보를 갖춘 개인사업자(같은 사업자번호 공유로 이력 대조 시나리오 겸용). */
    protected long seedApplication(
            long userId,
            boolean submitted,
            String rejectedWith,
            BusinessType businessType,
            String businessName,
            String businessRegNo) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            SellerApplication application = SellerApplication.Companion.draft(userId, businessType);
            application.updateDraft(
                    businessType,
                    businessName,
                    businessRegNo,
                    null,
                    "홍길동",
                    "2026-서울강남-0001",
                    "004",
                    Base64.getEncoder().encodeToString(piiCrypto.encrypt(ACCOUNT_NUMBER)),
                    "홍길동");
            application.registerDocument(
                    ApplicationDocument.Companion.of(DocumentType.ID_CARD, DOCUMENT_KEY, "image/png"));
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

    protected MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
