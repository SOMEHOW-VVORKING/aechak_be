package com.aechak.domain.product.category

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import com.aechak.domain.product.category.enums.CategoryStatus

@Entity
@Table(name = "categories")
class Category protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    val parent: Category?,
    val depth: Int,
    name: String,
    iconUrl: String?,
    sortOrder: Int,
) : AggregateRoot() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(length = 100, nullable = false)
    var name: String = name
        protected set

    @Column(length = 512)
    var iconUrl: String? = iconUrl
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: CategoryStatus = CategoryStatus.ACTIVE
        protected set

    var sortOrder: Int = sortOrder
        protected set

    @Version
    var version: Long = 0L
        protected set


    companion object {
        fun create(parent: Category?, depth: Int, name: String, iconUrl: String?, sortOrder: Int): Category {
            if (depth !in 1..3) {
                throw BusinessException(ProductErrorCode.INVALID_CATEGORY_DEPTH)
            }
            val rootLevel = depth == 1
            if (rootLevel != (parent == null)) {
                throw BusinessException(ProductErrorCode.INVALID_CATEGORY_DEPTH)
            }
            return Category(parent, depth, name, iconUrl, sortOrder)
        }
    }
}
