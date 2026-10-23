package com.aechak.application.order.delivery.port

/**
 * 택배사 추적 조회 창구. 택배사 코드와 송장번호로 그 송장의 현재 추적 결과를 묻는다.
 * 구현은 infra/client — 외부사의 요청·응답 모양은 이 계약 밖으로 새지 않는다.
 */
interface ShipmentTrackingPort {
    /**
     * 송장 하나의 추적 결과. 택배사가 그 송장을 아직 모르면 null —
     * 송장 등록 직후나 오입력된 번호에서 흔하게 일어나므로 예외가 아니다.
     */
    fun fetchTracking(
        courierCode: String,
        trackingNumber: String,
    ): ShipmentTrackingSnapshot?
}

/** 추적 조회 결과. 지금 쓰는 값은 최근 상태 문구 하나다. 단계 목록은 기본값을 가진 필드로 여기에 더한다. */
data class ShipmentTrackingSnapshot(
    /** 택배사가 준 최근 상태 문구. 허용값이 없는 자유 문자열이다 */
    val lastStatus: String,
) {
    init {
        // 빈 문구를 담으면 화면에는 "상태 있음"으로 보이고 내용은 없다 — 모르는 것은 null(조회 실패)로만 표현한다
        require(lastStatus.isNotBlank()) { "추적 상태 문구는 비어 있을 수 없습니다." }
    }
}
