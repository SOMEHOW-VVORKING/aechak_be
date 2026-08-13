package com.aechak.application.product.usecase

import com.aechak.application.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.usecase.query.SellerProductSearchQuery
import com.aechak.application.product.usecase.result.ProductRegisterResult
import com.aechak.application.product.usecase.result.SellerProductOptionsResult
import com.aechak.application.product.usecase.result.SellerProductSummaryResult
import com.aechak.application.support.OffsetPageResult

interface SellerProductUseCase {
    fun registerProduct(command: RegisterProductCommand): ProductRegisterResult

    /** 내 상품 목록 조회 — 옵셔널 필터 + 정렬 + 오프셋 페이지네이션 */
    fun getMyProducts(query: SellerProductSearchQuery): OffsetPageResult<SellerProductSummaryResult>

    /** 내 상품의 옵션 조합별 재고 조회 */
    fun getMyProductOptions(
        sellerId: Long,
        publicId: String,
    ): SellerProductOptionsResult
}
