package com.aechak.infra.persistence.payment

import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.enums.PaymentStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 계약 테스트. 깨지면 저장이 merge라 매핑에서 빠진 컬럼이 NULL로 덮임.
 * 승인 뒤 재저장에서 실결제 금액과 취소가능액이 사라지는 경로라 값 하나씩 단언함.
 */
class PaymentMapperTest {
    @Test
    fun `승인 전 결제는 null 컬럼을 그대로 두고 왕복한다`() {
        val prepared =
            Payment.prepare(
                orderGroupId = 7L,
                paymentId = "01K2ZQZQZQZQZQZQZQZQZQZQZQ",
                method = PaymentMethod.KAKAO_PAY,
                targetAmount = 13_000L,
            )

        assertRoundTrip(prepared)
    }

    @Test
    fun `승인된 결제는 전 컬럼을 그대로 두고 왕복한다`() {
        val approved =
            Payment.restore(
                id = 42L,
                orderGroupId = 7L,
                paymentId = "01K2ZQZQZQZQZQZQZQZQZQZQZQ",
                method = PaymentMethod.CARD,
                targetAmount = 13_000L,
                status = PaymentStatus.APPROVED,
                transactionId = "pg-tx-1",
                realPaidAmount = 13_000L,
                failureCode = null,
                cancellableAmount = 13_000L,
                version = 3,
            )

        assertRoundTrip(approved)
    }

    @Test
    fun `실패한 결제는 failureCode를 잃지 않고 왕복한다`() {
        val failed =
            Payment.restore(
                id = 43L,
                orderGroupId = 8L,
                paymentId = "01K2ZQZQZQZQZQZQZQZQZQZQZR",
                method = PaymentMethod.CARD,
                targetAmount = 13_000L,
                status = PaymentStatus.FAILED,
                transactionId = null,
                realPaidAmount = null,
                failureCode = "PAY_PROCESS_ABORTED",
                cancellableAmount = null,
                version = 2,
            )

        assertRoundTrip(failed)
    }

    private fun assertRoundTrip(origin: Payment) {
        val restored = PaymentMapper.toDomain(PaymentMapper.toEntity(origin))

        assertEquals(origin.id, restored.id, "id가 달라지면 재저장이 insert로 새 행을 만든다")
        assertEquals(origin.orderGroupId, restored.orderGroupId, "주문그룹을 잃으면 결제와 주문을 못 잇는다")
        assertEquals(origin.paymentId, restored.paymentId, "PG 조회 키다")
        assertEquals(origin.method, restored.method, "사용자가 고른 결제수단이다")
        assertEquals(origin.targetAmount, restored.targetAmount, "금액 위변조 검증 기준이다")
        assertEquals(origin.status, restored.status, "승인 여부 판정의 기준이다")
        assertEquals(origin.transactionId, restored.transactionId, "PG 거래를 역추적할 유일한 키다")
        assertEquals(origin.realPaidAmount, restored.realPaidAmount, "환불 가능 여부의 근거다")
        assertEquals(origin.failureCode, restored.failureCode, "실패 원인 분석의 근거다")
        assertEquals(origin.cancellableAmount, restored.cancellableAmount, "초과 취소 방어선이다")
        assertEquals(origin.version, restored.version, "version을 잃으면 저장이 낡은 값으로 나가 매번 충돌한다")
    }
}
