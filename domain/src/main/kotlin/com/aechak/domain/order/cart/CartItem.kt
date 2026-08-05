package com.aechak.domain.order.cart

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_cart_items_cart_id_option_combination_id",
            columnNames = ["cart_id", "option_combination_id"],
        ),
    ],
)
class CartItem protected constructor(
    optionCombinationId: Long,
    quantity: Int,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false)
    val optionCombinationId: Long = optionCombinationId

    @Column(nullable = false)
    var quantity: Int = quantity
        protected set

    fun accumulate(addedQuantity: Int) {
        quantity = validatedQuantity(currentQuantity = quantity, addedQuantity = addedQuantity)
    }

    companion object {
        /** 라인당 수량 상한. 품목 종류 상한(100)과 단위가 다름. */
        const val MAX_QUANTITY = 99

        fun of(
            optionCombinationId: Long,
            quantity: Int,
        ): CartItem = CartItem(optionCombinationId, validatedQuantity(currentQuantity = 0, addedQuantity = quantity))

        /**
         * 하한은 담을 수량을, 상한은 합계를 봄. 수량 0 재담기는 합계가 1 이상이어도 막아야 해서 갈림.
         * 음수도 하한에서 걸림. 수량을 깎는 것은 담기가 아니라 수량 변경의 몫.
         */
        private fun validatedQuantity(
            currentQuantity: Int,
            addedQuantity: Int,
        ): Int {
            if (addedQuantity < 1) {
                throw BusinessException(OrderErrorCode.INVALID_CART_ITEM_QUANTITY)
            }
            val total = currentQuantity + addedQuantity
            if (total > MAX_QUANTITY) {
                throw BusinessException(CommonErrorCode.INVALID_REQUEST)
            }
            return total
        }
    }
}
