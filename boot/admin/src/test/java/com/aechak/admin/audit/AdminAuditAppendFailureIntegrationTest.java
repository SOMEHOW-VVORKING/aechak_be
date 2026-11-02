package com.aechak.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.SellerReviewIntegrationTestBase;
import com.aechak.domain.audit.auditlog.AdminAuditLog;
import com.aechak.domain.audit.auditlog.repository.AdminAuditLogRepository;
import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import com.aechak.domain.seller.seller.Seller;
import com.aechak.domain.seller.seller.enums.SellerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * [통합] <b>기록 실패가 조치를 되돌리지 않는다</b>는 결정을 고정한다.
 *
 * <p>고른 쪽: 기록 실패를 삼키고 조치는 그대로 둔다. 반대쪽(기록 실패 시 조치까지 롤백)을 고르면
 * 감사 테이블의 사고 한 번이 모든 운영 조치를 멈춘다 — 승인·승격·반려가 전부 500이 되고,
 * 운영자는 실패한 줄 알고 다시 누른다.
 *
 * <p>그 대가는 "빠진 줄 모르는 것"이고, 그래서 삼키는 자리는 조치를 손으로 복원할 수 있는 재료를
 * ERROR 로그로 남긴다. 이 테스트가 고정하는 것은 그 선택의 <b>관찰 가능한 절반</b>이다 —
 * 기록이 실패해도 응답은 성공이고 승인의 부작용(셀러 개점·신청서 전이)이 전부 일어났는가.
 */
@Import(AdminAuditAppendFailureIntegrationTest.FailingAuditRepositoryConfig.class)
class AdminAuditAppendFailureIntegrationTest extends SellerReviewIntegrationTestBase {

    @Test
    void 감사_기록이_실패해도_승인은_성공하고_부작용이_전부_일어난다() throws Exception {
        long applicantId = createUser();
        long applicationId = seedApplication(applicantId, true);

        mockMvc.perform(bearer(post(BASE + "/" + applicationId + "/approve"), adminToken))
                .andExpect(status().isNoContent());

        tx.executeWithoutResult(txStatus -> {
            assertThat(em.find(SellerApplication.class, applicationId).getStatus())
                    .as("기록 실패가 신청서 전이를 되돌렸다")
                    .isEqualTo(ApplicationStatus.APPROVED);
            assertThat(em.find(Seller.class, applicantId).getStatus())
                    .as("기록 실패가 셀러 개점을 되돌렸다")
                    .isEqualTo(SellerStatus.ACTIVE);
        });
        assertThat(auditRowCount())
                .as("기록은 실패했어야 한다 — 실패를 심지 못하면 이 테스트가 아무것도 고정하지 않는다")
                .isZero();
    }

    private long auditRowCount() {
        return tx.execute(txStatus -> em.createQuery("select count(l) from AdminAuditLog l", Long.class)
                .getSingleResult());
    }

    /** 감사 쓰기만 터지게 한다 — 조치 경로는 실 어댑터 그대로 둔다 */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditRepositoryConfig {

        @Bean
        @Primary
        AdminAuditLogRepository failingAuditLogRepository() {
            return new AdminAuditLogRepository() {
                @Override
                public AdminAuditLog append(AdminAuditLog log) {
                    throw new IllegalStateException("감사 테이블 쓰기 사고를 가정한다");
                }
            };
        }
    }
}
