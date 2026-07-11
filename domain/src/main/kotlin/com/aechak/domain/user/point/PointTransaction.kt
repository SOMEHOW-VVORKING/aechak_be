package com.aechak.domain.user.point

import com.aechak.common.error.BusinessException
import com.aechak.domain.support.BaseEntity
import com.aechak.domain.user.error.UserErrorCode
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
import jakarta.persistence.UniqueConstraint
import com.aechak.domain.user.point.enums.PointTransactionType
import com.aechak.domain.user.user.User

@Entity
@Table(
    name = "point_transactions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_point_transactions_idempotency_key", columnNames = ["idempotency_key"]),
    ],
)
class PointTransaction protected constructor(
    buyer: User,
    amount: Long,
    transactionType: PointTransactionType,
    sourceType: String?,
    sourceId: Long?,
    idempotencyKey: String,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    val buyer: User = buyer

    @Column(nullable = false)
    val amount: Long = amount

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    val transactionType: PointTransactionType = transactionType

    @Column(length = 30)
    val sourceType: String? = sourceType

    /** 다형 참조 — 연관 불가. */
    @Column
    val sourceId: Long? = sourceId

    @Column(name = "idempotency_key", length = 100, nullable = false)
    val idempotencyKey: String = idempotencyKey

    companion object {
        fun record(
            buyer: User,
            amount: Long,
            transactionType: PointTransactionType,
            idempotencyKey: String,
            sourceType: String? = null,
            sourceId: Long? = null,
        ): PointTransaction {
            if (amount <= 0) {
                throw BusinessException(UserErrorCode.INVALID_POINT_AMOUNT)
            }
            return PointTransaction(buyer, amount, transactionType, sourceType, sourceId, idempotencyKey)
        }
    }
}
