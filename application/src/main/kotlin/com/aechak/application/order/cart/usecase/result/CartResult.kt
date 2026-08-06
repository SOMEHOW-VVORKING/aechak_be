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
        val price: Long,
        val discountRate: Int?,
        /** 마스킹하지 않은 잔여 재고. 담아둔 수량이 잔여를 넘는지 클라이언트가 판정해야 함. */
        val remainingStock: Int,
        val itemStatus: CartItemStatus,
        val isOrderable: Boolean,
    )

    companion object {
        /** createdAt은 갱신하지 않는 컬럼이라 재담기로 수량만 늘어난 항목은 자리가 움직이지 않음. */
        private val newestFirst =
            compareByDescending<Pair<CartItem, CartCatalogItemView>> { (item, _) -> item.createdAt }
                .thenByDescending { (item, _) -> item.id }

        /** 카탈로그에 없는 항목은 뺀 뒤 조립하므로 cartItemCount도 남은 항목만 셈. 안 그러면 뱃지가 화면과 어긋남. */
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
                // 그룹 순서는 따로 정하지 않고 항목 순서에서 파생함. groupBy가 첫 등장 순서를 지킴
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
                price = view.unitPriceAt(now),
                discountRate = view.pricing().discountRateAt(now),
                remainingStock = view.stockQuantity,
                itemStatus = itemStatus,
                isOrderable = itemStatus == CartItemStatus.ACTIVE,
            )
        }
    }
}
