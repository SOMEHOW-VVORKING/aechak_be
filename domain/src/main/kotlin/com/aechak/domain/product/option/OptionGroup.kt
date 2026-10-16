package com.aechak.domain.product.option

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
    name = "option_groups",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_option_group_product_name", columnNames = ["product_id", "name"]),
    ],
)
class OptionGroup protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
    name: String,
    sortOrder: Int,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(length = NAME_MAX, nullable = false)
    var name: String = name
        protected set

    var sortOrder: Int = sortOrder
        protected set

    var isActive: Boolean = true
        protected set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "option_group_id", nullable = false, updatable = false)
    private val _values: MutableList<OptionValue> = mutableListOf()
    val values: List<OptionValue> get() = _values.toList()

    companion object {
        const val NAME_MAX = 100

        fun create(
            product: Product,
            name: String,
            sortOrder: Int,
            valueNames: List<String> = emptyList(),
        ): OptionGroup =
            OptionGroup(product, name, sortOrder).apply {
                valueNames.forEachIndexed { index, valueName -> _values += OptionValue.of(valueName, index) }
            }
    }
}
