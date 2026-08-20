package com.aechak.application.order.usecase

import com.aechak.application.order.usecase.command.CreateOrderGroupCommand
import com.aechak.application.order.usecase.result.CreateOrderGroupResult

/**
 * order 도메인의 유일한 진입점 계약. 규칙은 user 도메인 템플릿(UserUseCase) 참조.
 * 입출력 어휘(command/·result/·query/)는 이 패키지 하위에 둔다 — 계약은 usecase/, 구현은 service/.
 */
interface OrderUseCase {
    /** 같은 멱등키의 재요청이면 새로 만들지 않고 최초 생성 결과를 돌려준다. */
    fun createOrderGroup(command: CreateOrderGroupCommand): CreateOrderGroupResult
}
