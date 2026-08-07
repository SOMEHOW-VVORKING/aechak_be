package com.aechak.application.order.cart.usecase.result

import com.aechak.application.order.cart.port.view.CartCatalogItemView
import com.aechak.domain.order.cart.CartItem
import com.aechak.domain.order.cart.enums.CartItemStatus
import java.time.LocalDateTime

data class CartResult(
    /** 담긴 수량의 합계. 품목 종류 수가 아님. */
    val cartItemCount: Int,
    val sellerGroups: List<SellerGroupResult>,
) {
    data class SellerGroupResult(
        val sellerId: Long,
        val storeName: String,
        val baseShippingFee: Long,
        val freeShippingThreshold: Long?,
        val items: List<CartItemResult>,
    )

    data class CartItemResult(
        val cartItemId: Long,
        /** products.public_id (ULID) */
        val productId: String,
        val optionCombinationId: Long,
        val productName: String,
        val thumbnail: String?,
        val selectedOptions: String,
        val quantity: Int,
        val originalPrice: Long,
        val price: Long,
        /** 상품 정가 기준. 옵션 추가금이 붙은 originalPrice, price와 기준이 다름. */
        val discountRate: Int?,
        /** 마스킹하지 않은 정확한 값. 상품 옵션 조회와 다름. */
        val remainingStock: Int,
        val itemStatus: CartItemStatus,
        val isOrderable: Boolean,
    )

    companion object {
        /** createdAt은 갱신하지 않는 컬럼이라 재담기로 수량만 늘어난 항목은 자리가 움직이지 않음. */
        private val newestFirst =
            compareByDescending<Pair<CartItem, CartCatalogItemView>> { (item, _) -> item.createdAt }
                .thenByDescending { (item, _) -> item.id }

        /** 카탈로그에 없는 항목은 뺀 뒤 조립하므로 cartItemCount도 남은 항목만 셈. */
        fun from(
            items: List<CartItem>,
            catalog: Map<Long, CartCatalogItemView>,
            now: LocalDateTime,
            resolveThumbnail: (String?) -> String?,
        ): CartResult {
            val rows =
                items
                    .mapNotNull { item -> catalog[item.optionCombinationId]?.let { item to it } }
                    .sortedWith(newestFirst)
            return CartResult(
                cartItemCount = rows.sumOf { (item, _) -> item.quantity },
                // groupBy가 첫 등장 순서를 지켜 그룹 순서가 항목 순서를 따라감
                sellerGroups =
                    rows
                        .groupBy { (_, view) -> view.sellerId }
                        .map { (_, sellerRows) -> toSellerGroup(sellerRows, now, resolveThumbnail) },
            )
        }

        private fun toSellerGroup(
            sellerRows: List<Pair<CartItem, CartCatalogItemView>>,
            now: LocalDateTime,
            resolveThumbnail: (String?) -> String?,
        ): SellerGroupResult {
            val seller = sellerRows.first().second
            return SellerGroupResult(
                sellerId = seller.sellerId,
                storeName = seller.storeName,
                baseShippingFee = seller.baseShippingFee,
                freeShippingThreshold = seller.freeShippingThreshold,
                items = sellerRows.map { (item, view) -> toItem(item, view, now, resolveThumbnail) },
            )
        }

        private fun toItem(
            item: CartItem,
            view: CartCatalogItemView,
            now: LocalDateTime,
            resolveThumbnail: (String?) -> String?,
        ): CartItemResult {
            val itemStatus = view.itemStatus()
            return CartItemResult(
                cartItemId = item.id,
                productId = view.productPublicId,
                optionCombinationId = item.optionCombinationId,
                productName = view.productName,
                thumbnail = resolveThumbnail(view.representativeImageKey),
                selectedOptions = view.optionName,
                quantity = item.quantity,
                originalPrice = view.originalPriceAt(),
                price = view.unitPriceAt(now),
                discountRate = view.pricing().discountRateAt(now),
                remainingStock = view.stockQuantity,
                itemStatus = itemStatus,
                isOrderable = itemStatus == CartItemStatus.ACTIVE,
            )
        }
    }
}
