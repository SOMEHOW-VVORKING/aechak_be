package com.aechak.application.inspection.port;

import com.aechak.domain.inspection.enums.InspectionTargetType;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 집계 창과 쪼개는 축.
 *
 * <p>비율 지표의 창 기준은 전부 {@code content_inspections.settled_at}이다. 앵커를 섞으면
 * (승급률·비용은 호출 시각, 누락률은 검수 시각) 창 경계에 걸친 건이 지표마다 다른 버킷에
 * 들어가고, 더 결정적으로 호출 원장에는 {@code policy_version}이 없어 정책 버전별 비교를
 * 원장 앵커로는 할 수 없다. 그래서 원장은 {@code inspection_id}로 붙인다.
 *
 * <p>{@code from}은 포함, {@code to}는 배타다. 두 창을 이어 붙였을 때 경계의 한 건이 양쪽에
 * 들어가면 합이 전체보다 커진다.
 *
 * <p>{@code policyVersion}·{@code targetType}이 비면 전체다.
 */
public record InspectionMetricsWindow(
        LocalDateTime from, LocalDateTime to, String policyVersion, InspectionTargetType targetType) {

    public static InspectionMetricsWindow of(LocalDateTime from, LocalDateTime to) {
        return new InspectionMetricsWindow(from, to, null, null);
    }

    /**
     * 창 길이(시간). 응답이 창을 사람이 읽을 수 있는 단위로 함께 적는다.
     *
     * <p>절삭이다. 한 시간보다 짧은 창은 0이 되므로 {@link #minutes()}를 함께 싣는다 — 이 값만
     * 보면 30분 창과 창이 없는 상태가 같은 글자가 된다.
     */
    public int hours() {
        return (int) Math.max(0, Duration.between(from, to).toHours());
    }

    /** 창 길이(분). 시간으로 절삭되는 창을 읽는 쪽이 복원할 수 있는 축이다 */
    public long minutes() {
        return Math.max(0, Duration.between(from, to).toMinutes());
    }
}
