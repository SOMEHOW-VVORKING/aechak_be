package com.aechak.application.seller.port;

import com.aechak.application.seller.port.view.AdminSellerView;
import com.aechak.application.seller.port.view.SellerSanctionView;
import java.util.List;

/**
 * 어드민 셀러 계정 조회 포트. 애그리거트 계약이 아니라 운영 화면 전용 읽기 모델이라
 * domain 리포지토리가 아니라 소비자(application)가 정의한다.
 */
public interface AdminSellerQueryPort {

    /** 셀러 식별자 내림차순(개점 역순) 한 페이지. */
    List<AdminSellerView> findSellerPage(AdminSellerPageCondition condition);

    /** 같은 조건의 전체 행 수 — 운영자가 "조건에 맞는 셀러 N명"을 봐야 한다. */
    long countSellers(AdminSellerPageCondition condition);

    /** 셀러 1건 — 없으면 null. */
    AdminSellerView findSeller(long sellerId);

    /** 판매정지 이력 — 최신순(id 내림차순). */
    List<SellerSanctionView> findSanctionHistory(long sellerId, int limit);
}
