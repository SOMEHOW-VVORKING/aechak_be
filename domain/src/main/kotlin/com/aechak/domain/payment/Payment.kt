package com.aechak.domain.payment

/**
 * 결제 도메인 모델 — payment는 헥사고날 완전 적용(L3) 예외 도메인이다.
 * 순수 도메인 모델과 JPA 영속 모델을 분리하고 매퍼로 잇는다 — 다른 도메인과 달리 @Entity를 여기 두지 않는다.
 * 영속 모델·매퍼는 infra의 persistence 어댑터 소속. 결제 구현 착수 시 전용 규칙 문서를 작성한다.
 */
class Payment(
    val id: Long = 0L,
    // TODO: 결제 구현 착수 시 필드·불변식 정의 (payments·payment_cancellations)
)
