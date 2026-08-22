package com.aechak.domain.product.option

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.option.event.OptionCombinationChangedEvent
import com.aechak.domain.product.product.Product
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "option_combinations",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_option_combination_signature", columnNames = ["product_id", "value_signature"]),
    ],
)
class OptionCombination protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
    @Column(length = 255, nullable = false)
    val name: String,
    additionalPrice: Long,
    stockQuantity: Int,
    @Column(length = 255, nullable = false)
    val valueSignature: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    var additionalPrice: Long = additionalPrice
        protected set

    @Column(nullable = false, columnDefinition = "int unsigned")
    var stockQuantity: Int = stockQuantity
        protected set

    var isActive: Boolean = true
        protected set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "option_combination_id", nullable = false, updatable = false)
    private val _values: MutableList<OptionCombinationValue> = mutableListOf()
    val values: List<OptionCombinationValue> get() = _values.toList()

    /**
     * 잔량보다 큰 차감 -> 거절하지 않고 0에서 멈추고 반영한 값을 리턴.
     */
    fun changeStock(delta: Int): Int {
        val before = stockQuantity
        stockQuantity = (before.toLong() + delta).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return stockQuantity - before
    }

    fun activate() {
        isActive = true
    }

    fun deactivate() {
        isActive = false
    }

    fun registerChangedEvent() {
        registerEvent(OptionCombinationChangedEvent(product.id, id))
    }

    companion object {
        /** 재고가 해당 개수 이하면 노출(임계치) */
        const val STOCK_DISPLAY_THRESHOLD = 20

        fun create(
            product: Product,
            name: String,
            additionalPrice: Long,
            stockQuantity: Int,
            valueSignature: String,
            optionValues: List<OptionValue> = emptyList(),
        ): OptionCombination {
            if (additionalPrice < 0L) {
                throw BusinessException(ProductErrorCode.INVALID_OPTION_ADDITIONAL_PRICE)
            }
            if (stockQuantity < 0) {
                throw BusinessException(ProductErrorCode.INVALID_OPTION_STOCK)
            }
            return OptionCombination(product, name, additionalPrice, stockQuantity, valueSignature)
                .apply { optionValues.forEach { _values += OptionCombinationValue.of(it) } }
        }
    }
}
