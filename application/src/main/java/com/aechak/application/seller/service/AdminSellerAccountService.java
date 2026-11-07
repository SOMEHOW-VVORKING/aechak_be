package com.aechak.application.seller.service;

import com.aechak.application.audit.support.AdminAuditContextHolder;
import com.aechak.application.seller.port.AdminSellerPageCondition;
import com.aechak.application.seller.port.AdminSellerQueryPort;
import com.aechak.application.seller.port.view.AdminSellerView;
import com.aechak.application.seller.port.view.SellerSanctionView;
import com.aechak.application.seller.usecase.command.ReleaseSellerSuspensionCommand;
import com.aechak.application.seller.usecase.command.SaveSellerMemoCommand;
import com.aechak.application.seller.usecase.command.SuspendSellerCommand;
import com.aechak.application.seller.usecase.query.AdminSellerSearchQuery;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.seller.error.SellerErrorCode;
import com.aechak.domain.seller.sanction.SellerSanction;
import com.aechak.domain.seller.sanction.repository.SellerSanctionRepository;
import com.aechak.domain.seller.seller.Seller;
import com.aechak.domain.seller.seller.repository.SellerRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 어드민 셀러 계정 관리 로직 보관함 — Facade에서만 호출된다. 전역 자원 접근이라 소유권 검증이 없다. */
@Service
@RequiredArgsConstructor
public class AdminSellerAccountService {

    private final SellerRepository sellerRepository;
    private final SellerSanctionRepository sellerSanctionRepository;
    private final AdminSellerQueryPort adminSellerQueryPort;

    public List<AdminSellerView> findSellerPage(AdminSellerSearchQuery query) {
        return adminSellerQueryPort.findSellerPage(toCondition(query));
    }

    public long countSellers(AdminSellerSearchQuery query) {
        return adminSellerQueryPort.countSellers(toCondition(query));
    }

    public AdminSellerView findSeller(long sellerId) {
        AdminSellerView view = adminSellerQueryPort.findSeller(sellerId);
        if (view == null) {
            throw new BusinessException(SellerErrorCode.SELLER_NOT_FOUND, null, null);
        }
        return view;
    }

    public List<SellerSanctionView> findSanctionHistory(long sellerId, int limit) {
        return adminSellerQueryPort.findSanctionHistory(sellerId, limit);
    }

    /**
     * 판매정지. 검증을 이력 행 생성(입력)과 상태 전이(도메인) 두 단계로 나눠 두고 둘 다 통과한 뒤에만
     * 저장한다 — 상태만 바뀌고 이력이 없는 행이 생기면 분쟁에서 쓸 수 없다(회원 제재와 같은 순서).
     */
    public void suspend(SuspendSellerCommand command) {
        Seller seller = loadSeller(command.sellerId());
        // 만료 시각은 서버 시계로 만든다 — 클라이언트가 보낸 시각을 믿지 않는다
        LocalDateTime until =
                command.durationDays() == null ? null : LocalDateTime.now().plusDays(command.durationDays());
        SellerSanction history = SellerSanction.Companion.suspend(
                seller.getUserId(),
                command.reasonCode(),
                command.reasonDetail(),
                until,
                command.memo(),
                command.adminId());
        seller.suspend(command.reasonCode().describe(command.reasonDetail()), until);
        overwriteMemoIfPresent(seller, command.memo());
        sellerSanctionRepository.save(history);
    }

    /** 판매정지 해제 — 해제도 UPDATE가 아니라 이력 한 행으로 남긴다. */
    public void releaseSuspension(ReleaseSellerSuspensionCommand command) {
        Seller seller = loadSeller(command.sellerId());
        SellerSanction history = SellerSanction.Companion.release(
                seller.getUserId(), command.reasonDetail(), command.memo(), command.adminId());
        seller.releaseSuspension();
        overwriteMemoIfPresent(seller, command.memo());
        sellerSanctionRepository.save(history);
    }

    /** 메모만 저장 — 이력 행을 남기지 않는다. 누가 언제 바꿨는지는 감사 로그가 받는다. */
    public void saveMemo(SaveSellerMemoCommand command) {
        loadSeller(command.sellerId()).updateAdminMemo(command.memo());
    }

    /**
     * 조치 공통 선행 조회. 감사 행에 남길 그 시점의 상호명도 여기서 집는다 — 조치보다 먼저 올려 둬야
     * 거부당한 시도의 행에도 대상 이름이 남는다.
     */
    private Seller loadSeller(long sellerId) {
        Seller seller = sellerRepository.findByUserId(sellerId);
        if (seller == null) {
            throw new BusinessException(SellerErrorCode.SELLER_NOT_FOUND, null, null);
        }
        AdminAuditContextHolder.INSTANCE.label(seller.getStoreName());
        return seller;
    }

    /**
     * 제재 조치에 딸려 온 메모는 현재 메모를 덮어쓴다 — 화면의 메모 칸과 같은 값이다.
     * 빈 값으로는 지우지 않는다: 정지 대화상자를 메모 없이 확인한 것이 기존 메모를 날리면 안 된다
     * (메모를 지우는 경로는 메모 저장이다).
     */
    private void overwriteMemoIfPresent(Seller seller, String memo) {
        if (memo != null && !memo.isBlank()) {
            seller.updateAdminMemo(memo);
        }
    }

    private AdminSellerPageCondition toCondition(AdminSellerSearchQuery query) {
        return new AdminSellerPageCondition(
                query.status(),
                query.typeFilter().businessTypes(),
                query.storeNameKeyword(),
                (long) query.page() * query.size(),
                query.size());
    }
}
