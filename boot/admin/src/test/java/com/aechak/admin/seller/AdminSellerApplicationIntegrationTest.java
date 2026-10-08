package com.aechak.admin.seller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.SellerReviewIntegrationTestBase;
import com.aechak.domain.user.user.enums.UserRole;
import org.junit.jupiter.api.Test;

/** 어드민 신청 목록·상세 통합 테스트. */
class AdminSellerApplicationIntegrationTest extends SellerReviewIntegrationTestBase {

    @Test
    void 목록을_status로_거르고_제출일_내림차순으로_준다() throws Exception {
        long first = seedApplication(createUser(), true);
        long second = seedApplication(createUser(), true);
        seedApplication(createUser(), false); // DRAFT — 필터 밖

        mockMvc.perform(bearer(get(BASE).param("status", "SUBMITTED"), adminToken))
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

        mockMvc.perform(bearer(get(BASE).param("page", "1").param("size", "2"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void 상세는_계좌_전체_서류_다운로드_URL_동일_사업자번호_이력을_준다() throws Exception {
        long previousId = seedApplication(createUser(), true, "서류 재제출 요망");
        long applicationId = seedApplication(createUser(), true);

        mockMvc.perform(bearer(get(BASE + "/" + applicationId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.data.documents[0].documentType").value("ID_CARD"))
                .andExpect(jsonPath("$.data.documents[0].downloadUrl")
                        .value("https://fake-download.local/" + DOCUMENT_KEY))
                .andExpect(jsonPath("$.data.reviews.length()").value(0))
                .andExpect(jsonPath("$.data.previousApplications.length()").value(1))
                .andExpect(
                        jsonPath("$.data.previousApplications[0].applicationId").value(previousId));
    }

    @Test
    void 반려된_신청_상세엔_심사_이력과_반려_사유가_실린다() throws Exception {
        long applicationId = seedApplication(createUser(), true, "통장사본 불일치");

        mockMvc.perform(bearer(get(BASE + "/" + applicationId), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("통장사본 불일치"))
                .andExpect(jsonPath("$.data.reviews.length()").value(1))
                .andExpect(jsonPath("$.data.reviews[0].decision").value("REJECTED"));
    }

    @Test
    void 없는_신청_상세는_404_10100_를_반환한다() throws Exception {
        mockMvc.perform(bearer(get(BASE + "/999999"), adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(10100));
    }

    @Test
    void 일반_유저_토큰은_목록_접근이_403_20011_으로_막힌다() throws Exception {
        mockMvc.perform(bearer(get(BASE), mintAccessToken(createUser(), UserRole.GENERAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(20011));
    }
}
