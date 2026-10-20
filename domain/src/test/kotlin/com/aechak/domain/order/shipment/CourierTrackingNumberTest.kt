package com.aechak.domain.order.shipment

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.shipment.enums.TrackingNumberCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 송장번호 형식 검증 계약. 택배사 마스터의 패턴 컬럼은 운영이 채우므로 비어 있거나 깨진 값이 들어올 수 있다 —
 * 그 경우 검증을 건너뛰어야 셀러의 송장 등록이 마스터 데이터 때문에 막히지 않는다.
 * 건너뛴 사유는 반환값으로 드러난다. 로그는 호출자(application)가 남긴다.
 */
class CourierTrackingNumberTest {
    @Test
    fun `패턴이 없으면 어떤 송장번호도 통과한다`() {
        val check = courier(trackingFormatPattern = null).validateTrackingNumber("아무값")

        assertEquals(TrackingNumberCheck.SKIPPED_NO_PATTERN, check)
    }

    @Test
    fun `패턴에 맞는 송장번호는 통과한다`() {
        val check = courier(trackingFormatPattern = "\\d{12}").validateTrackingNumber("123456789012")

        assertEquals(TrackingNumberCheck.MATCHED, check)
    }

    @Test
    fun `패턴에 어긋나는 송장번호는 거절된다`() {
        val exception =
            assertFailsWith<BusinessException> {
                courier(trackingFormatPattern = "\\d{12}").validateTrackingNumber("12345")
            }

        assertEquals(OrderErrorCode.INVALID_TRACKING_NUMBER, exception.errorCode)
    }

    @Test
    fun `패턴 일부만 맞는 송장번호는 거절된다`() {
        val exception =
            assertFailsWith<BusinessException> {
                courier(trackingFormatPattern = "\\d{12}").validateTrackingNumber("123456789012A")
            }

        assertEquals(OrderErrorCode.INVALID_TRACKING_NUMBER, exception.errorCode)
    }

    @Test
    fun `정규식으로 컴파일되지 않는 패턴은 검증을 건너뛰고 사유를 알린다`() {
        val check = courier(trackingFormatPattern = "\\d{12").validateTrackingNumber("abc")

        assertEquals(TrackingNumberCheck.SKIPPED_BROKEN_PATTERN, check)
    }

    private fun courier(trackingFormatPattern: String?): CourierMaster =
        CourierMaster.register(
            courierCode = "CJ",
            courierName = "CJ대한통운",
            trackingFormatPattern = trackingFormatPattern,
        )
}
