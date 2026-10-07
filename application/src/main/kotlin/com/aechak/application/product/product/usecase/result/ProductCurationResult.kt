package com.aechak.application.product.product.usecase.result

data class ProductCurationResult(
    val recommended: List<ProductSummaryResult>,
    val ranking: List<ProductSummaryResult>,
) {
    companion object {
        /** 인기 랭킹 노출 개수 */
        const val RANKING_SIZE = 5

        /** 무작위 추천 노출 개수 */
        const val RECOMMENDED_SIZE = 8
    }
}
