package com.aechak.application.product.product.usecase

import com.aechak.application.product.product.usecase.command.ChangeOptionCombinationCommand
import com.aechak.application.product.product.usecase.command.ChangeProductSaleStatusCommand
import com.aechak.application.product.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.product.usecase.command.UpdateProductCommand
import com.aechak.application.product.product.usecase.query.SellerProductSearchQuery
import com.aechak.application.product.product.usecase.result.OptionCombinationChangeResult
import com.aechak.application.product.product.usecase.result.ProductRegisterResult
import com.aechak.application.product.product.usecase.result.ProductSaleStatusChangeResult
import com.aechak.application.product.product.usecase.result.ProductUpdateResult
import com.aechak.application.product.product.usecase.result.SellerProductDetailResult
import com.aechak.application.product.product.usecase.result.SellerProductInspectionResult
import com.aechak.application.product.product.usecase.result.SellerProductOptionsResult
import com.aechak.application.product.product.usecase.result.SellerProductStatusCountsResult
import com.aechak.application.product.product.usecase.result.SellerProductSummaryResult
import com.aechak.application.support.OffsetPageResult

interface SellerProductUseCase {
    fun registerProduct(command: RegisterProductCommand): ProductRegisterResult

    fun updateProduct(command: UpdateProductCommand): ProductUpdateResult

    fun changeProductSaleStatus(command: ChangeProductSaleStatusCommand): ProductSaleStatusChangeResult

    fun changeOptionCombination(command: ChangeOptionCombinationCommand): OptionCombinationChangeResult

    /** 내 상품 목록 조회 — 옵셔널 필터 + 정렬 + 오프셋 페이지네이션 */
    fun getMyProducts(query: SellerProductSearchQuery): OffsetPageResult<SellerProductSummaryResult>

    /** 내 상품 단건 조회 — 수정 폼 프리필용 */
    fun getMyProductDetail(
        sellerId: Long,
        publicId: String,
    ): SellerProductDetailResult

    /** 내 상품의 옵션 조합별 재고 조회 */
    fun getMyProductOptions(
        sellerId: Long,
        publicId: String,
    ): SellerProductOptionsResult

    /**
     * 대시보드용 상품 상태 요약 — 검수 대기·반려·판매 중지·품절을 쿼리 한 번으로 센다.
     * 기간 필터가 없는 현재 스냅샷이고 네 건수는 서로 배타적이지 않다.
     *
     * 전제 — 셀러 자격 게이트는 호출자가 통과시킨다. 집계는 넘겨받은 sellerId 범위만 읽고
     * 식별자를 돌려주지 않으므로 소유권 판정 대상이 아니다. 이 계약을 HTTP로 직접 노출하지 않는다.
     */
    fun getMyProductStatusCounts(sellerId: Long): SellerProductStatusCountsResult

    /** 내 상품의 검수 결과 — 반려 사유와 안내 문구. 검수를 타지 않은 상품은 검수 부분이 빈다 */
    fun getMyProductInspection(
        sellerId: Long,
        publicId: String,
    ): SellerProductInspectionResult
}
