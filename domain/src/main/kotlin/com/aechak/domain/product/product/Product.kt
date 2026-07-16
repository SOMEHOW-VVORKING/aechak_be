package com.aechak.domain.product.product

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
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
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(
    name = "products",
    uniqueConstraints = [UniqueConstraint(name = "uk_product_public_id", columnNames = ["public_id"])],
)
class Product protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    val category: Category,
    val sellerId: Long,
    name: String,
    description: String?,
    representativeImageKey: String?,
    regularPrice: Long,
    discountPrice: Long?,
    discountStartAt: LocalDateTime?,
    discountEndAt: LocalDateTime?,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false, length = 26)
    val publicId: String = Ulid.generate()

    @Column(length = 255, nullable = false)
    var name: String = name
        protected set

    @Column(columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @Column(length = 1024)
    var representativeImageKey: String? = representativeImageKey
        protected set

    var regularPrice: Long = regularPrice
        protected set

    var discountPrice: Long? = discountPrice
        protected set

    var discountStartAt: LocalDateTime? = discountStartAt
        protected set

    var discountEndAt: LocalDateTime? = discountEndAt
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var saleStatus: SaleStatus = SaleStatus.ON_SALE
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var inspectionStatus: InspectionStatus = InspectionStatus.APPROVED
        protected set

    @Version
    var version: Int = 0
        protected set

    @OneToMany(cascade = [jakarta.persistence.CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private val _images: MutableList<ProductImage> = mutableListOf()
    val images: List<ProductImage> get() = _images.toList()

    companion object {
        fun register(
            category: Category,
            sellerId: Long,
            name: String,
            description: String?,
            representativeImageKey: String?,
            regularPrice: Long,
            discountPrice: Long?,
            discountStartAt: LocalDateTime?,
            discountEndAt: LocalDateTime?,
        ): Product {
            if (regularPrice < 0L) {
                throw BusinessException(ProductErrorCode.INVALID_PRODUCT_PRICE)
            }
            if (discountPrice != null && (discountPrice < 0L || discountPrice > regularPrice)) {
                throw BusinessException(ProductErrorCode.INVALID_PRODUCT_PRICE)
            }
            return Product(
                category = category,
                sellerId = sellerId,
                name = name,
                description = description,
                representativeImageKey = representativeImageKey,
                regularPrice = regularPrice,
                discountPrice = discountPrice,
                discountStartAt = discountStartAt,
                discountEndAt = discountEndAt,
            )
        }
    }
}
