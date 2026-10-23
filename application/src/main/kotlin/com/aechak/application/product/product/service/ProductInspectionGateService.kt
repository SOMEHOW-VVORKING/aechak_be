package com.aechak.application.product.product.service

import com.aechak.application.inspection.port.ProductGateOutcome
import com.aechak.application.inspection.port.ProductInspectionGate
import com.aechak.common.error.BusinessException
import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.repository.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 판정과 관리자 결정을 상품 노출 상태로 옮긴다.
 *
 * 호출부의 트랜잭션 안에서 돌아야 한다. 판정 적용과 노출 변경이 갈라지면 판정은 적혔는데
 * 상품은 그대로인 상태가 남는다.
 *
 * 쓰기이므로 잠금 조회로 읽는다. 판정 적용이 검수 행을 먼저 잠그고 여기서 상품 행을 잠근다.
 * 기한 정리는 검수 행을 잠그지 않고 커밋 때 갱신하므로, 같은 검수에 판정이 동시에 오면 교착이 날 수 있음.
 * MySQL이 한쪽을 되돌리고, 판정은 재전달로 정리는 다음 주기로 다시 돎.
 */
@Component
class ProductInspectionGateService(
    private val productRepository: ProductRepository,
    /**
     * 노출 게이트 스위치. 기본은 꺼짐이다.
     *
     * 요청 발행 스위치와 따로 둔 이유는 판정을 쌓아 품질을 보는 단계와 구매자 화면에
     * 반영하는 단계를 나눠야 하기 때문이다. 한 스위치로 묶으면 검증 없이 켜는 수밖에 없다.
     *
     * 관리자 결정은 이 스위치와 무관하게 반영함. 사람이 내린 결정이기 때문임.
     */
    @Value("\${inspection.gate-enabled:false}") private val gateEnabled: Boolean,
) : ProductInspectionGate {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * PENDING, PASS -> APPROVED
     * APPROVED, FLAG -> PENDING. 관리자가 볼 때까지 숨김
     * 그 밖의 조합 -> 상태 유지. 미판정, 실패는 이미 보이는 상품을 숨기지 않고 REJECTED는 관리자만 풂
     */
    override fun applyJudgment(
        productId: Long,
        judgment: InspectionJudgment,
    ): ProductGateOutcome? {
        if (!gateEnabled) return null

        val product = productRepository.findByIdForUpdate(productId)
        if (product == null) {
            // 판정이 도착하기 전에 상품이 사라질 수 있다. 판정 자체를 적는 것을 막을 일은 아니다.
            log.info("판정을 반영할 상품이 없다. productId={}", productId)
            return null
        }

        val before = product.inspectionStatus
        when {
            before == InspectionStatus.PENDING && judgment == InspectionJudgment.PASS -> product.approveInspection()
            before == InspectionStatus.APPROVED && judgment == InspectionJudgment.FLAG -> product.holdInspection()
        }
        saveIfChanged(product, before)

        return ProductGateOutcome(
            productName = product.name,
            sellerId = product.sellerId,
            needsAdminReview = judgment != InspectionJudgment.PASS || before == InspectionStatus.REJECTED,
        )
    }

    override fun approveByAdmin(productId: Long) {
        val product = getForUpdate(productId)
        val before = product.inspectionStatus
        product.approveInspection()
        saveIfChanged(product, before)
    }

    override fun rejectByAdmin(productId: Long) {
        val product = getForUpdate(productId)
        val before = product.inspectionStatus
        product.rejectInspection()
        saveIfChanged(product, before)
    }

    /** 갓 등록한 상품을 첫 판정이 올 때까지 구매자에게 보이지 않게 둠 */
    fun holdUntilInspected(product: Product) {
        if (!gateEnabled) return
        product.holdInspection()
        productRepository.save(product)
    }

    private fun getForUpdate(productId: Long): Product =
        productRepository.findByIdForUpdate(productId)
            ?: throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)

    private fun saveIfChanged(
        product: Product,
        before: InspectionStatus,
    ) {
        if (before == product.inspectionStatus) return

        productRepository.save(product)
        log.info(
            "검수 결과로 상품 노출을 바꿨다. productId={} {} -> {}",
            product.id,
            before,
            product.inspectionStatus,
        )
    }
}
