package com.aechak.application.inspection.port.view;

/**
 * 비용의 원재료. 단위는 백만분의 1 USD -- 센트로 적으면 건당 비용이 0으로 눌린다.
 *
 * <p>검수 건수를 따로 들고 있어야 건당 비용이 나온다. 호출 수로 나누면 재시도와 등급 승격이
 * 분모를 부풀려 건당 비용이 실제보다 싸게 보인다.
 *
 * <p>{@code cacheMissCallCount}는 비용이 조용히 뛰는 자리를 드러낸다. 캐시 접두부가 최소 길이
 * 밑으로 내려가면 {@code cache_control}이 에러 없이 무시되고 입력 토큰이 전액 과금된다.
 */
public record InspectionCostCounts(
        long callCount,
        long inspectionCount,
        long totalCostMicroUsd,
        long triageCostMicroUsd,
        long cacheMissCallCount) {

    public static InspectionCostCounts empty() {
        return new InspectionCostCounts(0, 0, 0, 0, 0);
    }
}
