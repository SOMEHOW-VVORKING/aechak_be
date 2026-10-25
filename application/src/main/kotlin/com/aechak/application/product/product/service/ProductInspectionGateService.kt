package com.aechak.application.product.product.service

import com.aechak.application.inspection.port.ProductAppealTarget
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
     * **끄는 것은 노출 전이 하나뿐이다.** 관리자 결정, 이의 접수, 관리자 검토 큐 등재는
     * 이 스위치를 보지 않는다. 사람이 내린 결정이거나 사람이 볼 목록을 채우는 일이고,
     * 판정을 쌓아 품질을 보는 단계에서 바로 그 목록이 필요하다.
     */
    @Value("\${inspection.gate-enabled:false}") private val gateEnabled: Boolean,
) : ProductInspectionGate {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * PENDING, 증거 있는 PASS -> APPROVED
     * APPROVED, FLAG -> PENDING. 관리자가 볼 때까지 숨김
     * 그 밖의 조합 -> 상태 유지. 미판정, 실패는 이미 보이는 상품을 숨기지 않고 REJECTED는 관리자만 풂
     *
     * 증거 없는 통과는 승인으로 올리지 않는다. 미판정·판정 실패와 같은 사실이기 때문이다.
     * 같은 이유로 이미 보이는 상품을 내리지도 않고 큐에만 올린다 — 모델이 보지 못한 것으로
     * 노출을 끊으면 모델 장애가 곧 매출 장애가 된다.
     *
     * **스위치가 끄는 것은 위의 상태 전이뿐이고 돌려주는 판단은 끄지 않는다.** 스위치 뒤에서
     * null을 돌려주면 큐 등재까지 함께 꺼지고, 그러면 꺼진 환경에는 열린 행이 한 건도 없어
     * 관리자가 반려할 행도 없고 어떤 상품도 반려에 닿지 못한다. 접수 자격의 신호가 둘(열린 행
     * 있음 / 관리자 반려) 뿐이라, 그 상태에서 셀러 이의 엔드포인트는 전건 409가 된다.
     */
    override fun applyJudgment(
        productId: Long,
        judgment: InspectionJudgment,
        modelVerified: Boolean,
    ): ProductGateOutcome? {
        val product = productRepository.findByIdForUpdate(productId)
        if (product == null) {
            // 판정이 도착하기 전에 상품이 사라질 수 있다. 판정 자체를 적는 것을 막을 일은 아니다.
            log.info("판정을 반영할 상품이 없다. productId={}", productId)
            return null
        }

        val approved = judgment == InspectionJudgment.PASS && modelVerified
        val before = product.inspectionStatus
        if (gateEnabled) {
            when {
                before == InspectionStatus.PENDING && approved -> product.approveInspection()
                before == InspectionStatus.APPROVED && judgment == InspectionJudgment.FLAG -> product.holdInspection()
            }
            saveIfChanged(product, before)
        }

        return ProductGateOutcome(
            productName = product.name,
            sellerId = product.sellerId,
            needsAdminReview = !approved || before == InspectionStatus.REJECTED,
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

    /**
     * 이의 접수를 위한 상품 행 잠금. 노출 상태를 바꾸지 않는다.
     *
     * 게이트 스위치를 보지 않는다 — 사람이 누르는 경로라서, 스위치가 꺼진 환경에서 null을
     * 돌려주면 이의가 조용히 아무것도 하지 않는다. 그 면제가 실제로 쓸모를 가지려면 자격을
     * 만드는 쪽(판정의 큐 등재·관리자 반려)도 함께 면제돼야 한다.
     */
    override fun lockForAppeal(productId: Long): ProductAppealTarget? =
        productRepository.findByIdForUpdate(productId)?.let {
            ProductAppealTarget(
                productName = it.name,
                sellerId = it.sellerId,
                adminRejected = it.inspectionStatus == InspectionStatus.REJECTED,
            )
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
