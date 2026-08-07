package com.aechak.api.order.cart.response

import com.aechak.application.order.cart.usecase.result.CartResult
import com.fasterxml.jackson.annotation.JsonProperty

data class CartResponse(
    val cartItemCount: Int,
    val sellerGroups: List<SellerGroupResponse>,
) {
    data class SellerGroupResponse(
        val sellerId: Long,
        val storeName: String,
        val baseShippingFee: Long,
        val freeShippingThreshold: Long?,
        val items: List<CartItemResponse>,
    ) {
        companion object {
            fun from(group: CartResult.SellerGroupResult): SellerGroupResponse =
                SellerGroupResponse(
                    sellerId = group.sellerId,
                    storeName = group.storeName,
                    baseShippingFee = group.baseShippingFee,
                    freeShippingThreshold = group.freeShippingThreshold,
                    items = group.items.map(CartItemResponse::from),
                )
        }
    }

    data class CartItemResponse(
        val cartItemId: Long,
        val productId: String,
        val optionCombinationId: Long,
        val productName: String,
        val thumbnail: String?,
        val selectedOptions: String,
        val quantity: Int,
        val price: Long,
        val discountRate: Int?,
        val remainingStock: Int,
        val itemStatus: String,
        @get:JsonProperty("isOrderable")
        val isOrderable: Boolean,
    ) {
        companion object {
            fun from(item: CartResult.CartItemResult): CartItemResponse =
                CartItemResponse(
                    cartItemId = item.cartItemId,
                    productId = item.productId,
                    optionCombinationId = item.optionCombinationId,
                    productName = item.productName,
                    thumbnail = item.thumbnail,
                    selectedOptions = item.selectedOptions,
                    quantity = item.quantity,
                    price = item.price,
                    discountRate = item.discountRate,
                    remainingStock = item.remainingStock,
                    itemStatus = item.itemStatus.name,
                    isOrderable = item.isOrderable,
                )
        }
    }

    companion object {
        fun from(result: CartResult): CartResponse =
            CartResponse(
                cartItemCount = result.cartItemCount,
                sellerGroups = result.sellerGroups.map(SellerGroupResponse::from),
            )
    }
}
