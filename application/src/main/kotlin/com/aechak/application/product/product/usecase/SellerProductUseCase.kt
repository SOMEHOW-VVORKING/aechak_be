package com.aechak.application.product.product.usecase

import com.aechak.application.product.product.usecase.command.AppealProductInspectionCommand
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

    /** 내 상품의 검수 결과 — 반려 사유와 안내 문구. 검수를 타지 않은 상품은 검수 부분이 빈다 */
    fun getMyProductInspection(
        sellerId: Long,
        publicId: String,
    ): SellerProductInspectionResult

    /**
     * 내 상품의 검수 차단에 이의를 접수한다. 접수는 노출을 바꾸지 않는다.
     *
     * 조회와 같은 타입을 돌려준다. FE가 접수 뒤 조회를 다시 부르지 않아도 되고, 둘이 같은
     * 사실(상품의 열린 검토 행 + 가장 최근 검수)을 보기 때문에 서로 어긋날 자리가 없다.
     */
    fun appealMyProductInspection(command: AppealProductInspectionCommand): SellerProductInspectionResult
}
