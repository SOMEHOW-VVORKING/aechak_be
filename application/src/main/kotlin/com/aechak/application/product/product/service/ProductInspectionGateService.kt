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
 * 쓰기이므로 잠금 조회로 읽는다. 판정 적용이 검수 행을 먼저 잠그고 여기서 상품 행을 잠그는데,
 * 반대 순서로 두 자원을 잡는 경로가 없어 교착이 생기지 않는다.
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
     * FLAG -> REJECTED
     * PASS, 관리자가 반려한 REJECTED -> 상태 유지. 사람이 내린 반려를 판정이 풀지 않음
     * PASS, 그 밖 -> APPROVED
     */
    override fun applyJudgment(
        productId: Long,
        judgment: InspectionJudgment,
        isAdminRejected: () -> Boolean,
    ): ProductGateOutcome? {
        if (!gateEnabled) return null

        val product = productRepository.findByIdForUpdate(productId)
        if (product == null) {
            // 판정이 도착하기 전에 상품이 사라질 수 있다. 판정 자체를 적는 것을 막을 일은 아니다.
            log.info("판정을 반영할 상품이 없다. productId={}", productId)
            return null
        }

        val before = product.inspectionStatus
        val keepsAdminRejection =
            judgment == InspectionJudgment.PASS && before == InspectionStatus.REJECTED && isAdminRejected()
        when {
            judgment == InspectionJudgment.FLAG -> product.rejectByInspection()
            judgment == InspectionJudgment.PASS && !keepsAdminRejection -> product.approveByInspection()
        }
        saveIfChanged(product, before)

        return ProductGateOutcome(
            productName = product.name,
            sellerId = product.sellerId,
            needsAdminReview = judgment == InspectionJudgment.FLAG || keepsAdminRejection,
        )
    }

    override fun approveByAdmin(productId: Long) {
        val product = getForUpdate(productId)
        val before = product.inspectionStatus
        product.approveByInspection()
        saveIfChanged(product, before)
    }

    override fun rejectByAdmin(productId: Long) {
        val product = getForUpdate(productId)
        val before = product.inspectionStatus
        product.rejectByInspection()
        saveIfChanged(product, before)
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
