package com.aechak.application.product.listener

import com.aechak.application.product.config.SaleStatusSyncAsyncConfig
import com.aechak.domain.product.option.event.OptionCombinationChangedEvent
import com.aechak.domain.product.option.repository.OptionCombinationRepository
import com.aechak.domain.product.product.repository.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OptionStockEventListener(
    private val productRepository: ProductRepository,
    private val optionCombinationRepository: OptionCombinationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 잠금이 먼저, 재고 조회가 뒤. 순서를 바꾸면 낡은 재고로 내린 판정이 뒤늦게 커밋돼 재고가 있는데 품절로 굳음.
     * `UPDATE`가 실행되지 않으면 `version`이 갱신되지 않으므로 낙관적 락으로는 막을 수 없어 행을 잠가 읽음.
     * AFTER_COMMIT을 빼면 비동기 스레드가 커밋 전 상태를 읽음.
     * saveNow는 커밋 시점 flush에서 터질 버전 충돌을 이 catch 안으로 끌어오려는 것.
     */
    @Async(SaleStatusSyncAsyncConfig.EXECUTOR_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: OptionCombinationChangedEvent) {
        try {
            val product = productRepository.findByIdForUpdate(event.productId) ?: return
            val hasStock = optionCombinationRepository.existsActiveStock(event.productId)
            product.syncSaleStatusWithStock(hasActiveStock = hasStock)
            productRepository.saveNow(product)
        } catch (e: Exception) {
            // 요청 스레드와 끊겨 있어 여기서 안 남기면 실패가 어디에도 안 드러남
            log.error("재고 파생 판매 상태 전환 실패. productId={}", event.productId, e)
        }
    }
}
