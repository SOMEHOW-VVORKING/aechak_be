package com.aechak.domain.payment

/** payment는 L3 예외 도메인 — @Entity를 여기 두지 않고 순수 모델↔JPA 영속 모델을 infra 어댑터가 매퍼로 잇는다(결제 구현 착수 시 필드·불변식 정의). */
class Payment(
    val id: Long = 0L,
)
