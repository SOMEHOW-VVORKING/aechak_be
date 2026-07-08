package com.aechak.application.payment.service

import com.aechak.application.payment.usecase.PaymentUseCase
import org.springframework.stereotype.Service

/**
 * PaymentUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 * PG 호출은 application이 정의한 포트(PaymentGatewayPort — 구현: infra/client/pg-client) 뒤에서 한다.
 * 규칙은 user 도메인 템플릿(UserFacade) 참조.
 */
@Service
class PaymentFacade(
    private val paymentService: PaymentService,
) : PaymentUseCase {
    // TODO: 유스케이스 구현
}
