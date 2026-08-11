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
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    var sortOrder: Int = sortOrder
        protected set

    companion object {
        const val STORAGE_KEY_MAX = 1024

        fun of(
            imageType: ProductImageType,
            storageKey: String,
            sortOrder: Int,
        ): ProductImage = ProductImage(imageType, storageKey, sortOrder)
    }
}
