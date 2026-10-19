package com.aechak.application.product.option.facade

import com.aechak.application.product.option.service.OptionStockService
import com.aechak.application.product.option.usecase.OptionStockUseCase
import com.aechak.application.product.option.usecase.command.DeductOptionStockCommand
import com.aechak.application.product.option.usecase.command.RestoreOptionStockCommand
import com.aechak.application.product.option.usecase.result.DeductOptionStockResult
import com.aechak.application.product.option.usecase.result.RestoreOptionStockResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * OptionStockUseCase의 유일한 구현체.
 * 재고 변이는 호출 도메인의 트랜잭션에 참여해야 하므로 REQUIRED다 — 주문 저장이 롤백되면 차감도 함께 돌아가야 한다.
 */
@Service
class OptionStockFacade(
    private val optionStockService: OptionStockService,
) : OptionStockUseCase {
    @Transactional
    override fun deductStock(command: DeductOptionStockCommand): DeductOptionStockResult = optionStockService.deductStock(command)

    @Transactional
    override fun restoreStock(command: RestoreOptionStockCommand): RestoreOptionStockResult = optionStockService.restoreStock(command)
}
