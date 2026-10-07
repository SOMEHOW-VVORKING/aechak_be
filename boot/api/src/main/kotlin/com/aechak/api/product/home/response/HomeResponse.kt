package com.aechak.api.product.home.response

import com.aechak.api.product.response.ProductSummaryResponse
import com.aechak.application.product.product.usecase.result.ProductCurationResult

data class HomeResponse(
    val recommended: List<ProductSummaryResponse>,
    val ranking: List<ProductSummaryResponse>,
) {
    companion object {
        fun from(result: ProductCurationResult): HomeResponse =
            HomeResponse(
                recommended = result.recommended.map(ProductSummaryResponse::from),
                ranking = result.ranking.map(ProductSummaryResponse::from),
            )
    }
}
