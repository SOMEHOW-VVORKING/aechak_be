package com.aechak.domain.order.shipment

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 송장번호 형식 검증 계약. 택배사 마스터의 패턴 컬럼은 운영이 채우므로 비어 있거나 깨진 값이 들어올 수 있다 —
 * 그 경우 검증을 건너뛰어야 셀러의 송장 등록이 마스터 데이터 때문에 막히지 않는다.
 */
class CourierTrackingNumberTest {
    @Test
    fun `패턴이 없으면 어떤 송장번호도 통과한다`() {
        courier(trackingFormatPattern = null).validateTrackingNumber("아무값")
    }

    @Test
    fun `패턴에 맞는 송장번호는 통과한다`() {
        courier(trackingFormatPattern = "\\d{12}").validateTrackingNumber("123456789012")
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
    fun `정규식으로 컴파일되지 않는 패턴은 검증을 건너뛴다`() {
        courier(trackingFormatPattern = "\\d{12").validateTrackingNumber("abc")
    }

    private fun courier(trackingFormatPattern: String?): CourierMaster =
        CourierMaster.register(
            courierCode = "CJ",
            courierName = "CJ대한통운",
            trackingFormatPattern = trackingFormatPattern,
        )
}
