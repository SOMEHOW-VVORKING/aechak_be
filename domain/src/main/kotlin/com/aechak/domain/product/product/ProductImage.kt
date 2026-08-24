package com.aechak.domain.product.product

import com.aechak.domain.product.product.enums.ProductImageType
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "product_images")
class ProductImage protected constructor(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val imageType: ProductImageType,
    @Column(length = STORAGE_KEY_MAX, nullable = false)
    val storageKey: String,
    sortOrder: Int,
    @Column(nullable = false)
    val fromVersionNo: Int,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    var sortOrder: Int = sortOrder
        protected set

    /** null이면 현재 노출분. 값이 있으면 그 버전까지 노출되고 닫힌 것. */
    @Column
    var toVersionNo: Int? = null
        protected set

    fun closeAt(lastVersionNo: Int) {
        toVersionNo = lastVersionNo
    }

    fun moveTo(sortOrder: Int) {
        this.sortOrder = sortOrder
    }

    companion object {
        const val STORAGE_KEY_MAX = 1024

        fun of(
            imageType: ProductImageType,
            storageKey: String,
            sortOrder: Int,
            fromVersionNo: Int,
        ): ProductImage = ProductImage(imageType, storageKey, sortOrder, fromVersionNo)
    }
}
