package com.aechak.application.seller.facade;

import com.aechak.application.file.port.enums.UploadPurpose;
import com.aechak.application.file.usecase.FileUseCase;
import com.aechak.application.seller.service.AdminSellerReviewService;
import com.aechak.application.seller.service.SellerApplicationService;
import com.aechak.application.seller.usecase.AdminSellerReviewUseCase;
import com.aechak.application.seller.usecase.result.AdminApplicationDetailResult;
import com.aechak.application.seller.usecase.result.AdminApplicationDocumentResult;
import com.aechak.application.seller.usecase.result.AdminApplicationPageResult;
import com.aechak.application.seller.usecase.result.AdminApplicationSummaryResult;
import com.aechak.application.seller.usecase.result.PreviousApplicationResult;
import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AdminSellerReviewUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 * 서류 다운로드 URL 발급(presign)은 네트워크 왕복 없는 로컬 서명이라 조회 트랜잭션 안에서 수행해도 안전하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSellerReviewFacade implements AdminSellerReviewUseCase {

    private final AdminSellerReviewService adminSellerReviewService;
    private final SellerApplicationService sellerApplicationService;
    private final FileUseCase fileUseCase;

    @Transactional(readOnly = true)
    @Override
    public AdminApplicationPageResult list(ApplicationStatus status, int page, int size) {
        return new AdminApplicationPageResult(
                adminSellerReviewService.findPage(status, page, size).stream()
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
}
