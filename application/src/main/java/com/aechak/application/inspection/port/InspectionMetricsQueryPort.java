package com.aechak.application.inspection.port;

import com.aechak.application.inspection.port.view.InspectionBacklogCounts;
import com.aechak.application.inspection.port.view.InspectionCostCounts;
import com.aechak.application.inspection.port.view.InspectionCoverageCounts;
import com.aechak.application.inspection.port.view.InspectionFunnelCounts;
import com.aechak.application.inspection.port.view.InspectionStageCost;
import com.aechak.application.inspection.port.view.InspectionTriageCounts;
import java.util.Map;

/**
 * 운영 지표 집계 포트 — 검수 행·관점 기록·호출 원장·검토 큐에서 세어 온다.
 *
 * <p>집계 테이블을 따로 두지 않고 질의 시점에 센다. 근거 넷이다. 판정은 비동기이고 기한 정리는
 * 10분 뒤에 닫으므로 버킷을 시각 T에 굳히면 T 버킷이 나중에 틀려진다. 관리자 결정·셀러 이의는
 * 며칠 뒤 같은 행에 붙어 과거 버킷을 다시 쓰게 만든다. 정책 버전·대상 종류로 쪼개는 것이 이
 * 지표의 쓸모인데 롤업은 축을 미리 고정해야 해서 축마다 백필이 된다. 그리고 파생 상태가
 * 원장과 어긋나는 것이 이 기능에서 반복된 결함 모양이다.
 *
 * <p>되돌아볼 지점을 숫자로 적어 둔다. 창 24시간의 원장 행이 10만을 넘거나 스냅샷 p95가 1초를
 * 넘으면 야간 롤업 테이블을 넣는다. 그때도 원장이 정본이다.
 *
 * <p><b>메서드를 쪼갠 이유는 분모가 서로 다르기 때문이다.</b> 퍼널·누락률은 창 안에 닫힌 검수,
 * 승급률은 선별 호출이 나간 검수, 적체는 지금 열려 있는 큐다. 한 쿼리로 묶으면 분모 하나가
 * 다른 지표의 값을 왜곡한다.
 *
 * <p><b>이 범위의 셈 단위는 검수 건이고 관점 행은 어떤 분모의 단위도 아니다.</b> 기한 정리가
 * 만료 건에 관점 행을 딱 하나({@code ALL}/{@code UNCHECKED}/{@code WAIT_LIMIT_EXCEEDED}) 적고
 * 닫는데, 요청이 실제로 몇 관점으로 나갔는지는 복원할 길이 없다 — 팬아웃은 AI 쪽 설정이고
 * 요청 메시지에도 검수 행에도 관점 목록이 없다. 검수 건을 단위로 두면 팬아웃 폭이 1이든 2이든
 * 분모 기여가 1로 같아서, 폭을 모르는 것이 분모를 흔들지 않는다. 관점 단위 지표(관점별 미검사율·
 * 관점별 커버리지)를 넣으려면 발행 시점의 팬아웃 폭을 검수 행에 적어야 하고, 그것은 요청 발행과
 * AI 계약을 함께 건드리는 일이라 이 범위를 넘는다. 만료 스윕이 관점 둘로 행을 쪼개 적는 대안은
 * 쓰지 않는다 — 폭을 모르고 적는 것이므로 거짓 기록이다.
 */
public interface InspectionMetricsQueryPort {

    /** 단계별 탈출 분포와 판정 분포 — 관점 기록 한 번의 묶음 조회로 전부 나온다. */
    InspectionFunnelCounts funnelCounts(InspectionMetricsWindow window);

    /** 승급률(1순위) — 선별 호출이 나간 검수와 그중 선별이 통과시킨 검수. */
    InspectionTriageCounts triageCounts(InspectionMetricsWindow window);

    /** 누락률 — 아무도 내용을 보지 않은 채 닫힌 검수. */
    InspectionCoverageCounts coverageCounts(InspectionMetricsWindow window);

    /** 비용 — 창 안에 닫힌 검수에 붙은 호출의 지출. */
    InspectionCostCounts costCounts(InspectionMetricsWindow window);

    /** 단계별 지출. 키는 호출 원장의 관점 이름이고 단계를 복원할 수 없는 행은 {@code UNATTRIBUTED}다. */
    Map<String, InspectionStageCost> stageCosts(InspectionMetricsWindow window);

    /** 관리자 대기 적체 — 창과 무관하게 지금 열려 있는 큐의 모양. */
    InspectionBacklogCounts backlogCounts();

    /**
     * 창 안 검수의 프롬프트 버전 분포.
     *
     * <p>정책 버전 리터럴이 백엔드 설정과 AI 정책 문구 두 곳에 손으로 적혀 있어 어긋날 수 있다.
     * 한 {@code policyVersion} 버킷에 프롬프트 버전이 둘 이상이면 그것이 어긋남이다.
     */
    Map<String, Long> promptVersionCounts(InspectionMetricsWindow window);
}
