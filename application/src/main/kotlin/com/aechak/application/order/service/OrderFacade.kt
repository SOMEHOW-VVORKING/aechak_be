package com.aechak.application.order.service

import com.aechak.application.order.usecase.OrderUseCase
import org.springframework.stereotype.Service

/**
 * OrderUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 * 타 도메인 협력(재고 검증 등)은 그쪽 UseCase를 주입받는다 — Service/Repository 직접 호출 금지.
 * 규칙은 user 도메인 템플릿(UserFacade) 참조.
 */
@Service
class OrderFacade(
    private val orderService: OrderService,
) : OrderUseCase {
    // TODO: 유스케이스 구현
}
