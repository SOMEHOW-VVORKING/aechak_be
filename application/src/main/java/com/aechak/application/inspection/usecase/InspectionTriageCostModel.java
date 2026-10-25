package com.aechak.application.inspection.usecase;

/**
 * 선별 계층의 비용 전제와 거기서 나온 손익분기 승급률 — <b>이 레포의 유일한 사본</b>이다.
 *
 * <p>정본은 AI 레포의 측정 하네스다. 그 값이 이 레포의 두 실행 모듈 설정과 유스케이스 기본값,
 * 그리고 설정 테스트의 상수까지 다섯 자리에 손으로 적혀 있었고, 테스트는 같은 레포의 yml을 같은
 * 레포의 리터럴과 비교하면서 주석으로는 "하네스를 다시 돌려 값이 바뀌면 이 테스트가 먼저 깨진다"고
 * 단언했다 — 하네스를 읽지 않으므로 거짓 보증이었다. 사본을 이 한 자리로 모으고, 두 모듈의 설정이
 * 이 값과 같은지만 테스트가 본다.
 *
 * <p><b>하네스 값이 바뀌면 이 파일을 손으로 고친다.</b> 두 레포를 묶는 CI 잡이 없으므로 그것이
 * 동기화 수단이고, 고치는 자리가 하나라 두 모듈이 서로 다른 기준으로 경보하는 모양은 생기지 않는다.
 *
 * <p>비용 둘을 함께 둔 이유는 손익분기가 거기서 나온 값이기 때문이다 — 셋 가운데 하나만 고치면
 * 숫자가 서로를 부정하는데, 그것을 테스트가 본다.
 */
public final class InspectionTriageCostModel {

    /** 선별 1회(Haiku)의 지출. 하네스 비용표의 선별 항목이다 */
    public static final long TRIAGE_CALL_MICRO_USD = 1_400;

    /** 선별이 아끼려는 호출 — Sonnet 2관점의 지출 */
    public static final long MODEL_PAIR_MICRO_USD = 3_680;

    /**
     * 손익분기 승급률. 이 위로 가면 선별은 비용을 줄이는 장치가 아니라 더하는 장치다.
     *
     * <p>{@code 1 - 1400/3680 = 0.6196}을 소수 셋째 자리로 올린 값이다. 같은 값이
     * 0.457·0.620·0.88 세 숫자로 유통된 적이 있었고, 낮은 값을 쓰면 승급률 45.7~62.0% 구간에서
     * 아직 이득인데도 "선별을 끄라"는 거짓 경보가 난다.
     */
    public static final double BREAKEVEN_PROMOTION_RATE = 0.620;

    private InspectionTriageCostModel() {}

    /** 비용 전제에서 다시 계산한 손익분기. 적어 둔 값과 어긋나면 셋 가운데 하나만 고친 것이다 */
    public static double breakevenFromCostModel() {
        return 1.0 - (double) TRIAGE_CALL_MICRO_USD / MODEL_PAIR_MICRO_USD;
    }
}
