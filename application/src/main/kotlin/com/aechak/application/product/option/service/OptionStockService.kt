package com.aechak.application.product.option.service

import com.aechak.application.product.option.usecase.command.DeductOptionStockCommand
import com.aechak.application.product.option.usecase.command.RestoreOptionStockCommand
import com.aechak.application.product.option.usecase.result.DeductOptionStockResult
import com.aechak.application.product.option.usecase.result.RestoreOptionStockResult
import com.aechak.application.product.option.usecase.result.RestoreOptionStockStatus
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.option.repository.OptionCombinationRepository
import org.springframework.stereotype.Service

/** 재고 변이의 최종 판정은 조건부 원자 UPDATE다 — 엔티티를 읽어 더하면 동시 주문에서 재고가 어긋난다. */
@Service
class OptionStockService(
    private val optionCombinationRepository: OptionCombinationRepository,
) {
    fun deductStock(command: DeductOptionStockCommand): DeductOptionStockResult =
        if (optionCombinationRepository.deductStock(command.optionCombinationId, command.quantity)) {
            DeductOptionStockResult.DEDUCTED
        } else {
            DeductOptionStockResult.INSUFFICIENT_STOCK
        }

    /** 0행은 상한 초과와 옵션조합 소멸 둘 다에서 나온다 — 원인을 단정하지 않고 현재 값을 읽어 가른다 */
    fun restoreStock(command: RestoreOptionStockCommand): RestoreOptionStockResult {
        if (optionCombinationRepository.restoreStock(command.optionCombinationId, command.quantity)) {
            return RestoreOptionStockResult.restored()
        }
        val current =
            optionCombinationRepository.findById(command.optionCombinationId)
                ?: return RestoreOptionStockResult(RestoreOptionStockStatus.OPTION_NOT_FOUND, null)
        val exceeded = current.stockQuantity > OptionCombination.MAX_STOCK_QUANTITY - command.quantity
        return RestoreOptionStockResult.of(
            status = if (exceeded) RestoreOptionStockStatus.STOCK_LIMIT_EXCEEDED else RestoreOptionStockStatus.UNKNOWN,
            currentStock = current.stockQuantity,
        )
    }
}
