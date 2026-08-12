package com.aechak.application.seller.facade;

import com.aechak.application.file.port.enums.UploadPurpose;
import com.aechak.application.file.usecase.FileUseCase;
import com.aechak.application.seller.service.AdminSellerReviewService;
import com.aechak.application.seller.service.SellerApplicationService;
import com.aechak.application.seller.service.SellerService;
import com.aechak.application.seller.usecase.AdminSellerReviewUseCase;
import com.aechak.application.seller.usecase.result.AdminApplicationDetailResult;
import com.aechak.application.seller.usecase.result.AdminApplicationDocumentResult;
import com.aechak.application.seller.usecase.result.AdminApplicationSummaryResult;
import com.aechak.application.seller.usecase.result.PreviousApplicationResult;
import com.aechak.application.settlement.usecase.SettlementUseCase;
import com.aechak.application.settlement.usecase.command.RegisterVerifiedAccountCommand;
import com.aechak.application.support.PageQuery;
import com.aechak.application.support.PageResult;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import com.aechak.domain.seller.error.SellerErrorCode;
import com.aechak.domain.seller.seller.Seller;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AdminSellerReviewUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 * 서류 다운로드 URL 발급(presign)은 네트워크 왕복 없는 로컬 서명이라 조회 트랜잭션 안에서 수행해도 안전하다.
 *
 * <p>승인·반려는 TransactionTemplate(프로그램적 경계)을 쓴다: 동시 처리의 낙관적 락·제약 위반은
 * 커밋 시점 flush에서 터져 선언적 경계 안의 catch로는 잡을 수 없다 — execute 밖 캐치에서
 * 10101(동시 처리 충돌)로 번역한다(SellerApplicationFacade의 10104 번역 선례).
 */
@Slf4j
@Service
public class AdminSellerReviewFacade implements AdminSellerReviewUseCase {

    private final AdminSellerReviewService adminSellerReviewService;
    private final SellerApplicationService sellerApplicationService;
    private final SellerService sellerService;
    private final SettlementUseCase settlementUseCase;
    private final FileUseCase fileUseCase;
    private final TransactionTemplate tx;

    public AdminSellerReviewFacade(
            AdminSellerReviewService adminSellerReviewService,
            SellerApplicationService sellerApplicationService,
            SellerService sellerService,
            SettlementUseCase settlementUseCase,
            FileUseCase fileUseCase,
            PlatformTransactionManager transactionManager) {
        this.adminSellerReviewService = adminSellerReviewService;
        this.sellerApplicationService = sellerApplicationService;
        this.sellerService = sellerService;
        this.settlementUseCase = settlementUseCase;
        this.fileUseCase = fileUseCase;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<AdminApplicationSummaryResult> list(ApplicationStatus status, PageQuery pageQuery) {
        return new PageResult<>(
                adminSellerReviewService.findPage(status, pageQuery).stream()
                        .map(AdminApplicationSummaryResult::from)
                        .toList(),
                adminSellerReviewService.count(status));
    }

    @Transactional(readOnly = true)
    @Override
    public AdminApplicationDetailResult detail(long adminId, long applicationId) {
        SellerApplication application = adminSellerReviewService.getById(applicationId);
        List<AdminApplicationDocumentResult> documents = application.getDocuments().stream()
                .map(document -> new AdminApplicationDocumentResult(
                        document.getDocumentType().name(),
                        document.getUpdatedAt(),
                        fileUseCase.issueDownloadUrl(document.getStorageKey(), UploadPurpose.SELLER_DOCUMENT)))
                .toList();
        if (!documents.isEmpty()) {
            // 경량 감사(design #9) — 열람 이력 테이블 대신 구조화 로그. 발급 경로는 어드민 게이트 뒤뿐이다.
            log.info(
                    "셀러 심사 서류 다운로드 URL 발급 adminId={} applicationId={} documentTypes={}",
                    adminId,
                    applicationId,
                    documents.stream().map(AdminApplicationDocumentResult::documentType).toList());
        }
        return AdminApplicationDetailResult.from(
                application,
                sellerApplicationService.decryptAccountNumber(application),
                documents,
                adminSellerReviewService.previousApplicationsOf(application).stream()
                        .map(PreviousApplicationResult::from)
                        .toList());
    }

    /** 승인 — 단일 tx: 전이·리뷰 기록 → 셀러 개점(ACTIVE) → 정산계좌 이관(VERIFIED, settlement UseCase 경유). */
    @Override
    public void approve(long adminId, long applicationId) {
        decideTranslatingConflicts(() -> tx.executeWithoutResult(txStatus -> {
            SellerApplication application = adminSellerReviewService.approve(adminId, applicationId);
            Seller seller = sellerService.open(
                    checkState(application.getUserId(), "신청서에 신청자가 없습니다 (applicationId=" + application.getId() + ")"),
                    // 상호명이 없는 개인 일반은 대표자명이 스토어명이 된다 — 제출 검증을 통과한 신청이라 null일 수 없다
                    checkState(
                            application.getBusinessName() != null
                                    ? application.getBusinessName()
                                    : application.getRepresentativeName(),
                            "스토어명 출처가 없습니다 (applicationId=" + application.getId() + ")"),
                    0);
            settlementUseCase.registerVerifiedAccount(new RegisterVerifiedAccountCommand(
                    seller.getUserId(),
                    checkState(application.getBankCode(), "제출 검증을 통과한 신청에 은행 코드가 없습니다"),
                    checkState(application.getAccountNumberEnc(), "제출 검증을 통과한 신청에 계좌가 없습니다"),
                    checkState(application.getAccountHolder(), "제출 검증을 통과한 신청에 예금주가 없습니다")));
        }));
    }

    @Override
    public void reject(long adminId, long applicationId, String reason) {
        decideTranslatingConflicts(() ->
                tx.executeWithoutResult(txStatus -> adminSellerReviewService.reject(adminId, applicationId, reason)));
    }

    /**
     * 동시 처리 충돌의 번역 — 계약상 전부 10101(409).
     * 낙관적 락만으론 부족하다: Hibernate flush는 INSERT를 version UPDATE보다 먼저 내보내므로
     * 동시 승인은 sellers PK·정산계좌 UNIQUE 중복으로도 나타난다. 무관한 제약까지 오라벨하지 않게 제약명으로 거른다.
     */
    private void decideTranslatingConflicts(Runnable block) {
        try {
            block.run();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED, e, null);
        } catch (DataIntegrityViolationException e) {
            String cause = e.getMostSpecificCause().getMessage() == null ? "" : e.getMostSpecificCause().getMessage();
            if (cause.contains("sellers.PRIMARY") || cause.contains("uk_settlement_accounts_seller_id")) {
                throw new BusinessException(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED, e, null);
            }
            throw e;
        }
    }

    /** Kotlin checkNotNull 대응 — 불변식 위반은 IllegalStateException(500)이 맞다(입력 검증 아님). */
    private static <T> T checkState(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
