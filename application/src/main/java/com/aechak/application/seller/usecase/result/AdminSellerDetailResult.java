package com.aechak.application.seller.usecase.result;

import com.aechak.application.seller.port.view.AdminSellerView;
import com.aechak.application.seller.port.view.SellerSanctionView;
import com.aechak.domain.seller.sanction.enums.SellerSanctionAction;
import com.aechak.domain.seller.sanction.enums.SellerSanctionReasonCode;
import com.aechak.domain.seller.seller.enums.SellerStatus;
import java.util.List;

/**
 * 셀러 상세 — 목록 값에 현재 정지의 사유·메모·제재 이력을 더한다.
 *
 * @param suspensionReasonCode 현재 정지를 건 사유 어휘. 화면의 사유 선택을 채우는 값이다. 정지 중이 아니면 비어 있다
 * @param suspensionReason 현재 정지 사유 한 줄(어휘 + 상세). 정지 중이 아니면 비어 있다
 * @param internalMemo 셀러의 현재 운영자 메모. 이력 행의 memo는 그 조치 시점의 사본이다
 */
public record AdminSellerDetailResult(
        AdminSellerSummaryResult seller,
        SellerSanctionReasonCode suspensionReasonCode,
        String suspensionReason,
        String internalMemo,
        List<SellerSanctionResult> sanctions) {

    public static AdminSellerDetailResult of(AdminSellerView view, List<SellerSanctionView> history) {
        return new AdminSellerDetailResult(
                AdminSellerSummaryResult.from(view),
                currentSuspensionReasonCode(view, history),
                view.suspensionReason(),
                view.adminMemo(),
                history.stream().map(SellerSanctionResult::from).toList());
    }

    /**
     * 현재 정지의 사유 어휘는 이력의 가장 최근 정지 행에서 읽는다 — sellers에 사유 코드를 또 두면
     * 같은 사실이 두 곳에 적히고 한쪽만 갱신되는 자리가 생긴다. 이력은 최신순이라 최초 일치 행이 그 조치다.
     */
    private static SellerSanctionReasonCode currentSuspensionReasonCode(
            AdminSellerView view, List<SellerSanctionView> history) {
        if (view.status() != SellerStatus.SUSPENDED) {
            return null;
        }
        return history.stream()
                .filter(row -> row.action() == SellerSanctionAction.SUSPEND)
                .map(SellerSanctionView::reasonCode)
                .findFirst()
                .orElse(null);
    }
}
