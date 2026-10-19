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
}
