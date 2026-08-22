package com.aechak.infra.persistence.payment

import com.aechak.domain.payment.Payment

/** 미매핑 컬럼은 merge 저장에서 덮이므로 전량 왕복시킴 */
internal object PaymentMapper {
    fun toEntity(payment: Payment): PaymentJpaEntity =
        PaymentJpaEntity(
            id = payment.id,
            orderGroupId = payment.orderGroupId,
            paymentId = payment.paymentId,
            method = payment.method,
            targetAmount = payment.targetAmount,
            status = payment.status,
            transactionId = payment.transactionId,
            realPaidAmount = payment.realPaidAmount,
            failureCode = payment.failureCode,
            cancellableAmount = payment.cancellableAmount,
            version = payment.version,
        )

    fun toDomain(entity: PaymentJpaEntity): Payment =
        Payment.restore(
            id = entity.id,
            orderGroupId = entity.orderGroupId,
            paymentId = entity.paymentId,
            method = entity.method,
            targetAmount = entity.targetAmount,
            status = entity.status,
            transactionId = entity.transactionId,
            realPaidAmount = entity.realPaidAmount,
            failureCode = entity.failureCode,
            cancellableAmount = entity.cancellableAmount,
            version = entity.version,
        )
}
