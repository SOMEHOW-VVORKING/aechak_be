package com.aechak.application.inspection.facade;

import com.aechak.application.inspection.port.AdminInspectionPageCondition;
import com.aechak.application.inspection.port.AdminInspectionQueryPort;
import com.aechak.application.inspection.port.view.AdminInspectionDetailView;
import com.aechak.application.inspection.usecase.AdminInspectionQueryUseCase;
import com.aechak.application.inspection.usecase.query.AdminInspectionSearchQuery;
import com.aechak.application.inspection.usecase.result.AdminInspectionDetailResult;
import com.aechak.application.inspection.usecase.result.AdminInspectionSummaryResult;
import com.aechak.application.inspection.usecase.result.InspectionPerspectiveResult;
import com.aechak.application.support.PageResult;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind;
import com.aechak.domain.inspection.repository.InspectionFindingRepository;
import com.aechak.domain.product.error.ProductErrorCode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AdminInspectionQueryUseCase}의 유일한 구현체. 읽기 전용이다.
 *
 * <p>파사드 패키지에 둔다 — 셀러 실행 모듈은 {@code ...inspection.service}만 스캔하므로 여기 두면
 * 셀러가 조립하지 않는다. 운영자 전용 조회라 셀러가 집을 이유가 없다(지표 파사드와 같은 판단).
 *
 * <p>관점 기록은 목록이 아니라 상세에서만 읽는다. 검수 한 건에 관점이 최대 다섯 행이라, 목록에
 * 실으면 한 페이지(최대 100행)에 500행을 더 끌어오면서 운영자가 목록에서 하는 판단(어디까지
 * 왔는지)에는 쓰이지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminInspectionQueryFacade implements AdminInspectionQueryUseCase {

    /**
     * 관점을 파이프라인이 도는 순서로 늘어놓는다.
     *
     * <p>열거 선언 순서는 이것과 다르고, 저장 순서도 보장이 없다. 화면에서 순서가 흔들리면 같은
     * 건을 두 번 볼 때 다른 줄을 비교하게 된다. 목록에 없는 관점이 생기면 맨 뒤로 간다.
     */
    private static final List<InspectionPerspectiveKind> PERSPECTIVE_ORDER = List.of(
            InspectionPerspectiveKind.RULE,
            InspectionPerspectiveKind.TRIAGE,
            InspectionPerspectiveKind.DISPLAY_ADS,
            InspectionPerspectiveKind.CONDUCT_TRADE,
            InspectionPerspectiveKind.ALL);

    private final AdminInspectionQueryPort adminInspectionQueryPort;
    private final InspectionFindingRepository inspectionFindingRepository;

    @Transactional(readOnly = true)
    @Override
    public PageResult<AdminInspectionSummaryResult> list(AdminInspectionSearchQuery query) {
        AdminInspectionPageCondition condition = query.toCondition();
        return new PageResult<>(
                adminInspectionQueryPort.findPage(condition).stream()
                        .map(AdminInspectionSummaryResult::from)
                        .toList(),
                adminInspectionQueryPort.count(condition));
    }

    @Transactional(readOnly = true)
    @Override
    public AdminInspectionDetailResult detail(long inspectionId) {
        AdminInspectionDetailView view = adminInspectionQueryPort.findDetail(inspectionId);
        if (view == null) {
            throw new BusinessException(ProductErrorCode.INSPECTION_NOT_FOUND, null, null);
        }
        return AdminInspectionDetailResult.of(view, perspectivesOf(inspectionId));
    }

    private List<InspectionPerspectiveResult> perspectivesOf(long inspectionId) {
        return inspectionFindingRepository.findByInspectionId(inspectionId).stream()
                .sorted(Comparator.comparingInt(finding -> orderOf(finding.getPerspective())))
                .map(InspectionPerspectiveResult::from)
                .toList();
    }

    private static int orderOf(InspectionPerspectiveKind perspective) {
        int index = PERSPECTIVE_ORDER.indexOf(perspective);
        return index < 0 ? PERSPECTIVE_ORDER.size() : index;
    }
}
