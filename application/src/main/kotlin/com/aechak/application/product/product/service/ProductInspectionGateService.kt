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
 * 쓰기이므로 잠금 조회로 읽는다. 판정 적용은 검수 행을 먼저 잠그고 여기서 상품 행을 잠근다.
 *
 * 반대 순서로 두 자원을 잡는 경로는 **있다.** 셀러 상품 수정이 상품 행을 먼저 잠그고 그 뒤
 * 검수를 쓴다. 지금 교착하지 않는 것은 그 경로의 검수 쓰기가 신규 INSERT 뿐이기 때문이다 —
 * 내용이 과거 값으로 되돌아오면 요청 발행이 비잠금 조회로 기존 행을 찾아 그대로 돌아가서,
 * 기존 검수 행에 쓰기 잠금을 걸지 않는다. 기존 검수 행을 다시 쓰는 전이(판정 실패·기한 만료
 * 건의 재요청)를 넣으면 그 순간 전형적인 교착이 되므로, 그때 두 경로의 잠금 순서를 함께 정해야 한다.
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
     * FLAG -> REJECTED
     * PASS, 관리자가 반려한 REJECTED -> 상태 유지. 사람이 내린 반려를 판정이 풀지 않음
     * PASS, 그 밖 -> APPROVED
     *
     * 상품 행을 잠근 뒤에 그 판정이 아직 최신 검수의 것인지 다시 본다. 잠그기 전 확인만으로는
     * 잠금을 기다리는 사이에 커밋된 셀러 수정과 새 검수를 못 봐서, 옛 내용의 판정이 새 내용의
     * 상품에 적용된다.
     *
     * **스위치가 끄는 것은 위의 상태 전이뿐이고 돌려주는 판단은 끄지 않는다.** 스위치 뒤에서
     * null을 돌려주면 큐 등재까지 함께 꺼지고, 그러면 꺼진 환경에는 열린 행이 한 건도 없어
     * 관리자가 반려할 행도 없고 어떤 상품도 반려에 닿지 못한다. 접수 자격의 신호가 둘(열린 행
     * 있음 / 관리자 반려) 뿐이라, 그 상태에서 셀러 이의 엔드포인트는 전건 409가 된다.
     */
    override fun applyJudgment(
        productId: Long,
        judgment: InspectionJudgment,
        isLatestJudgment: () -> Boolean,
        isAdminRejected: () -> Boolean,
    ): ProductGateOutcome? {
        val product = productRepository.findByIdForUpdate(productId)
        if (product == null) {
            // 판정이 도착하기 전에 상품이 사라질 수 있다. 판정 자체를 적는 것을 막을 일은 아니다.
            log.info("판정을 반영할 상품이 없다. productId={}", productId)
            return null
        }

        if (!isLatestJudgment()) {
            // 잠금을 기다리는 사이에 셀러가 고쳐 새 검수가 올라갔다.
            log.info("잠근 뒤 보니 최신 검수가 아니다. 반영하지 않는다. productId={}", productId)
            return null
        }

        val before = product.inspectionStatus
        val keepsAdminRejection =
            judgment == InspectionJudgment.PASS && before == InspectionStatus.REJECTED && isAdminRejected()

        if (gateEnabled) {
            when {
                judgment == InspectionJudgment.FLAG -> product.rejectByInspection()
                judgment == InspectionJudgment.PASS && !keepsAdminRejection -> product.approveByInspection()
            }
            saveIfChanged(product, before)
        }

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

    /**
     * 이의 접수를 위한 상품 행 잠금. 노출 상태를 바꾸지 않는다.
     *
     * 게이트 스위치를 보지 않는다 — 사람이 누르는 경로라서, 스위치가 꺼진 환경에서 null을
     * 돌려주면 이의가 조용히 아무것도 하지 않는다. 그 면제가 실제로 쓸모를 가지려면 자격을
     * 만드는 쪽(판정의 큐 등재·관리자 반려)도 함께 면제돼야 한다.
     *
     * 반려 상태와 관리자 결정 이력을 함께 본다. 반려는 AI가 내린 것일 수도 있어서 상태 하나로는
     * 사람이 내린 반려를 가를 수 없다.
     */
    override fun lockForAppeal(
        productId: Long,
        isAdminRejected: () -> Boolean,
    ): ProductAppealTarget? =
        productRepository.findByIdForUpdate(productId)?.let {
            ProductAppealTarget(
                productName = it.name,
                sellerId = it.sellerId,
                adminRejected = it.inspectionStatus == InspectionStatus.REJECTED && isAdminRejected(),
            )
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
