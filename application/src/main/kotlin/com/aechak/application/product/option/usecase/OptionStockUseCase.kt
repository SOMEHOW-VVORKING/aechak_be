package com.aechak.application.product.option.usecase

import com.aechak.application.product.option.usecase.command.DeductOptionStockCommand
import com.aechak.application.product.option.usecase.command.RestoreOptionStockCommand
import com.aechak.application.product.option.usecase.result.DeductOptionStockResult
import com.aechak.application.product.option.usecase.result.RestoreOptionStockResult

/**
 * 옵션 재고 변이의 진입점 계약. 재고는 product 도메인 소유라 주문처럼 자산을 쥐는 쪽은 반드시 이 UseCase를 경유한다.
 *
 * 두 메서드 모두 호출 트랜잭션에 참여한다(REQUIRED) — 최종 판정이 조건부 원자 UPDATE이고,
 * 그 성공·실패가 호출측의 주문 전이와 같은 트랜잭션에서 확정돼야 하기 때문이다.
 * 여러 건을 한 번에 다룰 때 호출측은 옵션조합 id 오름차순으로 호출해 교차 주문 간 데드락을 예방한다.
 */
interface OptionStockUseCase {
    fun deductStock(command: DeductOptionStockCommand): DeductOptionStockResult

    fun restoreStock(command: RestoreOptionStockCommand): RestoreOptionStockResult
}
