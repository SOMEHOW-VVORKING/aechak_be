package com.aechak.domain.product.option

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "option_values")
class OptionValue protected constructor(
    name: String,
    sortOrder: Int,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(length = 100, nullable = false)
    var name: String = name
        protected set

    var sortOrder: Int = sortOrder
        protected set

    var isActive: Boolean = true
        protected set

    companion object {
        fun of(
            name: String,
            sortOrder: Int,
        ): OptionValue = OptionValue(name, sortOrder)
    }
}
