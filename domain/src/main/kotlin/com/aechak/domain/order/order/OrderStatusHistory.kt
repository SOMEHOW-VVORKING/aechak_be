package com.aechak.domain.order.order

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.enums.OrderStatus

/**
 * [불변식] 이 행은 전이가 일어난 트랜잭션 안에서 동기 insert되어야 한다 — 어기면 createdAt이 실제
 * 전이 시각과 벌어진다(기록을 async/백필로 옮기려면 명시적 transitionedAt 컬럼을 되살려라).
 */
@Entity
@Table(name = "order_status_histories")
class OrderStatusHistory protected constructor(
    order: Order,
    fromStatus: OrderStatus?,
    toStatus: OrderStatus,
    triggeredBy: TriggeredBy,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order = order

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    val fromStatus: OrderStatus? = fromStatus

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val toStatus: OrderStatus = toStatus

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val triggeredBy: TriggeredBy = triggeredBy

    companion object {
        fun record(
            order: Order,
            fromStatus: OrderStatus?,
            toStatus: OrderStatus,
            triggeredBy: TriggeredBy,
        ): OrderStatusHistory = OrderStatusHistory(order, fromStatus, toStatus, triggeredBy)
    }
}
