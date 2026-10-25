package com.aechak.domain.order.shipment

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 택배사 상태 문구를 발송에 담을 모양으로 다듬는 규칙. 택배사가 주는 값은 허용값도 길이도 공백도
 * 보장되지 않는 자유 문자열이라, 그대로 담으면 저장에서 깨지거나 화면에 공백이 실린다.
 *
 * 기록 자체는 리포지토리의 원자 UPDATE가 두 컬럼만 짚어 쓴다 — 발송 행에는 배송완료 전이라는 다른
 * 쓰기 경로가 있어서, 애그리거트를 더티체킹으로 쓰면 둘이 서로의 컬럼을 옛 값으로 덮는다.
 * 그 계약은 배치 통합 테스트가 지킨다.
 */
class ShipmentTrackingStatusTest {
    @Test
    fun `택배사가 준 문구를 그대로 담는다`() {
        assertEquals("배송출발", Shipment.normalizeTrackingStatus("배송출발"))
    }

    @Test
    fun `앞뒤 공백은 떼고 담는다`() {
        assertEquals("배달완료", Shipment.normalizeTrackingStatus("  배달완료\n"))
    }

    @Test
    fun `컬럼 길이를 넘는 문구는 잘라서 담는다`() {
        val tooLong = "가".repeat(Shipment.MAX_LAST_TRACKING_STATUS_LENGTH + 10)

        assertEquals(
            Shipment.MAX_LAST_TRACKING_STATUS_LENGTH,
            Shipment.normalizeTrackingStatus(tooLong).length,
            "길이를 넘긴 문구를 그대로 담으면 저장에서 깨진다",
        )
    }

    @Test
    fun `공백을 뗀 뒤 길이를 센다`() {
        val padded = " " + "가".repeat(Shipment.MAX_LAST_TRACKING_STATUS_LENGTH) + " "

        assertEquals(
            Shipment.MAX_LAST_TRACKING_STATUS_LENGTH,
            Shipment.normalizeTrackingStatus(padded).length,
            "공백을 길이에 세면 끝 글자가 공백 때문에 잘려 나간다",
        )
    }
}
