package com.aechak.domain.order.shipment.enums

/**
 * 송장번호 형식 검증의 결과. 불일치는 예외로 나가므로 여기에는 통과한 경우만 담긴다.
 * 건너뛴 사유를 값으로 구분해 두는 것은 도메인이 로깅 수단을 갖지 않아 호출자가 경고를 남겨야 하기 때문이다.
 */
enum class TrackingNumberCheck {
    /** 패턴과 전체 일치했다. */
    MATCHED,

    /** 택배사에 패턴이 없어 검증하지 않았다. */
    SKIPPED_NO_PATTERN,

    /** 패턴이 정규식으로 컴파일되지 않아 검증하지 않았다. */
    SKIPPED_BROKEN_PATTERN,
}
