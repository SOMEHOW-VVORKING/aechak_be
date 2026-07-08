package com.aechak.application.payment.usecase

/**
 * payment 도메인의 유일한 진입점 계약. 규칙은 user 도메인 템플릿(UserUseCase) 참조.
 * 입출력 어휘(command/·result/·query/)는 이 패키지 하위에 둔다 — 계약은 usecase/, 구현은 service/.
 */
interface PaymentUseCase {
    // TODO: 기능 확정 시 메서드 추가 — PG 결제 승인/실패, 결제 취소 등 (멱등 처리 포함)
}
