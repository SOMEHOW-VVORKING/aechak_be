package com.aechak.admin.seller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.SellerReviewIntegrationTestBase;
import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import com.aechak.domain.seller.application.enums.BusinessType;
import com.aechak.domain.seller.seller.Seller;
import com.aechak.domain.seller.seller.enums.SellerStatus;
import com.aechak.domain.settlement.account.SettlementAccount;
import com.aechak.domain.settlement.account.enums.AccountVerificationStatus;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 승인·반려 통합 테스트 — 승인 부수효과(셀러 개점·계좌 이관)와 동시 처리 충돌(10101)을 실 DB 제약으로 검증한다. */
class AdminSellerReviewDecisionIntegrationTest extends SellerReviewIntegrationTestBase {

    @Test
    void 승인하면_한_트랜잭션으로_APPROVED_전이_셀러_개점_VERIFIED_정산계좌_이관이_일어난다() throws Exception {
        long userId = createUser();
        long applicationId = seedApplication(userId, true);

        mockMvc
                .perform(bearer(post(BASE + "/" + applicationId + "/approve"), adminToken))
                .andExpect(status().isNoContent());

        tx.executeWithoutResult(txStatus -> {
            SellerApplication application = em.find(SellerApplication.class, applicationId);
            assertEquals(ApplicationStatus.APPROVED, application.getStatus());
            assertEquals(1, application.getReviews().size());

            Seller seller = em.find(Seller.class, userId);
            assertEquals(SellerStatus.ACTIVE, seller.getStatus());
            assertEquals("애착상회", seller.getStoreName());
            assertEquals(0L, seller.getBaseShippingFee());

            SettlementAccount account = em
                    .createQuery(
                            "select a from SettlementAccount a where a.sellerId = :sellerId", SettlementAccount.class)
                    .setParameter("sellerId", userId)
                    .getSingleResult();
            assertEquals(AccountVerificationStatus.VERIFIED, account.getVerificationStatus());
            assertEquals(ACCOUNT_NUMBER, piiCrypto.decrypt(Base64.getDecoder().decode(account.getAccountNumberEnc())));
        });
    }

    @Test
    void 상호명_없는_개인_일반_승인은_대표자명이_스토어명이_된다() throws Exception {
        long userId = createUser();
        long applicationId = seedApplication(userId, true, null, BusinessType.PERSONAL_GENERAL, null, null);

        mockMvc
                .perform(bearer(post(BASE + "/" + applicationId + "/approve"), adminToken))
                .andExpect(status().isNoContent());

        tx.executeWithoutResult(txStatus ->
                assertEquals("홍길동", em.find(Seller.class, userId).getStoreName()));
    }

    @Test
    void 이미_처리된_신청의_재처리는_409_10101_를_반환한다() throws Exception {
        long applicationId = seedApplication(createUser(), true);
        mockMvc
                .perform(bearer(post(BASE + "/" + applicationId + "/approve"), adminToken))
                .andExpect(status().isNoContent());

        mockMvc
                .perform(bearer(post(BASE + "/" + applicationId + "/approve"), adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(10101));
        mockMvc
                .perform(rejectRequest(applicationId, "이미 승인된 건"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(10101));
    }

    @Test
    void 동시_승인_두_건은_한쪽만_성공하고_셀러는_하나만_생긴다() throws Exception {
        long userId = createUser();
        long applicationId = seedApplication(userId, true);

        ConcurrentLinkedQueue<Integer> statuses = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    statuses.add(mockMvc
                            .perform(bearer(post(BASE + "/" + applicationId + "/approve"), adminToken))
                            .andReturn()
                            .getResponse()
                            .getStatus());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS), "동시 승인 요청이 시간 안에 끝나야 한다");
        pool.shutdown();

        assertEquals(List.of(204, 409), statuses.stream().sorted().toList(), "한쪽은 승인, 다른 쪽은 동시 처리 충돌이어야 한다");
        tx.executeWithoutResult(txStatus -> {
            Long sellerCount = em
                    .createQuery("select count(s) from Seller s where s.userId = :userId", Long.class)
                    .setParameter("userId", userId)
                    .getSingleResult();
            assertEquals(1L, sellerCount);
        });
    }

    @Test
    void 반려_사유가_없거나_비면_400_10102_을_반환한다() throws Exception {
        long applicationId = seedApplication(createUser(), true);

        mockMvc
                .perform(rejectRequest(applicationId, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10102));
        mockMvc
                .perform(bearer(post(BASE + "/" + applicationId + "/reject"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10102));
    }

    @Test
    void 반려하면_REJECTED_전이와_사유_심사_이력이_남는다() throws Exception {
        long applicationId = seedApplication(createUser(), true);

        mockMvc
                .perform(rejectRequest(applicationId, "통장사본과 예금주가 다릅니다."))
                .andExpect(status().isNoContent());

        tx.executeWithoutResult(txStatus -> {
            SellerApplication application = em.find(SellerApplication.class, applicationId);
            assertEquals(ApplicationStatus.REJECTED, application.getStatus());
            assertEquals("통장사본과 예금주가 다릅니다.", application.getRejectionReason());
            assertEquals(1, application.getReviews().size());
        });
    }

    @Test
    void 반려_후_재제출된_신청을_승인하면_심사_이력이_누적된다() throws Exception {
        long userId = createUser();
        long applicationId = seedApplication(userId, true, "서류 보완 요망");
        tx.executeWithoutResult(txStatus -> {
            SellerApplication application = em.find(SellerApplication.class, applicationId);
            application.reopen();
            application.submit();
        });

        mockMvc
                .perform(bearer(post(BASE + "/" + applicationId + "/approve"), adminToken))
                .andExpect(status().isNoContent());

        tx.executeWithoutResult(txStatus -> {
            SellerApplication application = em.find(SellerApplication.class, applicationId);
            assertEquals(ApplicationStatus.APPROVED, application.getStatus());
            assertEquals(2, application.getReviews().size());
        });
    }

    private MockHttpServletRequestBuilder rejectRequest(long applicationId, String reason) {
        return bearer(post(BASE + "/" + applicationId + "/reject"), adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + reason + "\"}");
    }
}
