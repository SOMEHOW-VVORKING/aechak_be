package com.aechak.application.seller.service;

import com.aechak.common.error.BusinessException;
import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import com.aechak.domain.seller.application.repository.SellerApplicationRepository;
import com.aechak.domain.seller.error.SellerErrorCode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 어드민 심사 비즈니스 로직 보관함 — Facade에서만 호출된다. 전역 자원 접근이라 소유권 검증이 없다. */
@Service
@RequiredArgsConstructor
public class AdminSellerReviewService {

    private final SellerApplicationRepository sellerApplicationRepository;

    public SellerApplication getById(long applicationId) {
        SellerApplication application = sellerApplicationRepository.findById(applicationId);
        if (application == null) {
            throw new BusinessException(SellerErrorCode.SELLER_APPLICATION_NOT_FOUND, null, null);
        }
        return application;
    }

    public List<SellerApplication> findPage(ApplicationStatus status, int page, int size) {
        return sellerApplicationRepository.findPage(status, page, size);
    }

    public long count(ApplicationStatus status) {
        return sellerApplicationRepository.countAll(status);
    }

    /** 동일 사업자번호의 과거 신청 — 세탁 대조 보조(차단 아님). 자기 행은 빼고 최신순. */
    public List<SellerApplication> previousApplicationsOf(SellerApplication application) {
        String businessRegNo = application.getBusinessRegNo();
        if (businessRegNo == null) {
            return List.of();
        }
        return sellerApplicationRepository.findAllByBusinessRegNo(businessRegNo).stream()
                .filter(other -> other.getId() != application.getId())
                .sorted(Comparator.comparing(SellerApplication::getId).reversed())
                .toList();
    }
}
