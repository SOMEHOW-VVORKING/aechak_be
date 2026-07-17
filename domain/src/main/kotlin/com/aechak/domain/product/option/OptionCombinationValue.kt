package com.aechak.domain.product.option

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "option_combination_values")
class OptionCombinationValue protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_value_id", nullable = false)
    val optionValue: OptionValue,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    companion object {
        fun of(optionValue: OptionValue): OptionCombinationValue = OptionCombinationValue(optionValue)
    }
}
