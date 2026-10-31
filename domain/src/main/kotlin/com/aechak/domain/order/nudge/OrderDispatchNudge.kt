package com.aechak.domain.order.nudge

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 운영자가 셀러에게 발송을 독촉한 기록. append-only.
 *
 * 알림 발송은 하지 않는다 — 어드민 실행 모듈이 발송 어댑터를 조립하지 않아 보낼 수단이 없다.
 * 발송이 붙는 시점에 이 행을 발송 트리거의 근거로 쓴다.
 */
@Entity
@Table(
    name = "order_dispatch_nudges",
    indexes = [
        Index(name = "idx_order_dispatch_nudges_order_id", columnList = "order_id, id"),
    ],
)
class OrderDispatchNudge protected constructor(
    orderId: Long,
    adminUserId: Long,
    memo: String?,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false)
    val orderId: Long = orderId

    @Column(nullable = false, updatable = false)
    val adminUserId: Long = adminUserId

    @Column(length = MAX_MEMO_LENGTH)
    val memo: String? = memo

    companion object {
        const val MAX_MEMO_LENGTH = 300

        /** 공백만 들어온 메모는 없는 것으로 접는다 — 빈 문자열과 null이 섞이면 이력 표시가 갈린다. */
        fun record(
            orderId: Long,
            adminUserId: Long,
            memo: String?,
        ): OrderDispatchNudge {
            val normalized = memo?.trim()?.takeIf { it.isNotEmpty() }
            if (normalized != null && normalized.length > MAX_MEMO_LENGTH) {
                throw BusinessException(OrderErrorCode.ORDER_NUDGE_MEMO_TOO_LONG)
            }
            return OrderDispatchNudge(orderId, adminUserId, normalized)
        }
    }
}
