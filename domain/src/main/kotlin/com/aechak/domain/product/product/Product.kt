package com.aechak.domain.product.product

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.ProductImageType
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
import jakarta.persistence.CascadeType
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

    @Column(length = NAME_MAX, nullable = false)
    var name: String = name
        protected set

    @Column(columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @Column(length = ProductImage.STORAGE_KEY_MAX)
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

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private val _images: MutableList<ProductImage> = mutableListOf()
    val images: List<ProductImage> get() = _images.toList()

    /** sort_order는 image_type별로 UNIQUE라 종류마다 0부터 다시 매김. */
    private fun addImages(
        additionalImageKeys: List<String>,
        detailImageKeys: List<String>,
    ) {
        representativeImageKey?.let { _images += ProductImage.of(ProductImageType.REPRESENTATIVE, it, 0) }
        additionalImageKeys.forEachIndexed { index, key -> _images += ProductImage.of(ProductImageType.PRODUCT, key, index) }
        detailImageKeys.forEachIndexed { index, key -> _images += ProductImage.of(ProductImageType.DETAIL, key, index) }
    }

    /** 가격 계산 정책(정가/할인가/기간) */
    fun pricing(): ProductPricing =
        ProductPricing(
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = discountStartAt,
            discountEndAt = discountEndAt,
        )

    /** 할인이 적용되는 기간이면 할인가 반환, 이외에는 null 반환, 기간이 없는 경우 상시 할인으로 취급 */
    fun discountedPriceAt(at: LocalDateTime): Long? = pricing().discountedPriceAt(at)

    /** 현재 판매 가격 */
    fun sellingPriceAt(at: LocalDateTime): Long = pricing().sellingPriceAt(at)

    /** 표시용 현재 할인율(%) */
    fun discountRateAt(at: LocalDateTime): Int? = pricing().discountRateAt(at)

    companion object {
        const val NAME_MAX = 255

        const val REGULAR_PRICE_MIN = 100L
        const val REGULAR_PRICE_MAX = 100_000_000L
        const val ADDITIONAL_IMAGE_MAX = 9
        const val DETAIL_IMAGE_MAX = 20

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
            additionalImageKeys: List<String> = emptyList(),
            detailImageKeys: List<String> = emptyList(),
        ): Product {
            if (regularPrice !in REGULAR_PRICE_MIN..REGULAR_PRICE_MAX) {
                throw BusinessException(ProductErrorCode.INVALID_PRODUCT_PRICE)
            }
            if (additionalImageKeys.size > ADDITIONAL_IMAGE_MAX || detailImageKeys.size > DETAIL_IMAGE_MAX) {
                throw BusinessException(ProductErrorCode.TOO_MANY_PRODUCT_IMAGES)
            }
            if (discountPrice != null && (discountPrice !in 0L..regularPrice)) {
                throw BusinessException(ProductErrorCode.INVALID_PRODUCT_PRICE)
            }
            // 할인가가 있으면 시작일 필수(종료일은 선택 — 없으면 무기한). 상시할인은 불허.
            if (discountPrice != null && discountStartAt == null) {
                throw BusinessException(ProductErrorCode.INVALID_DISCOUNT_PERIOD)
            }
            // 역전 구간(시작 > 종료)은 어떤 시각에도 성립하지 않는 죽은 할인
            if (discountStartAt != null && discountEndAt != null && discountStartAt.isAfter(discountEndAt)) {
                throw BusinessException(ProductErrorCode.INVALID_DISCOUNT_PERIOD)
            }
            // 할인가 없이 기간(시작·종료 어느 쪽이든)만 있는 데이터는 무의미
            if (discountPrice == null && (discountStartAt != null || discountEndAt != null)) {
                throw BusinessException(ProductErrorCode.INVALID_DISCOUNT_PERIOD)
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
            ).apply { addImages(additionalImageKeys, detailImageKeys) }
        }
    }
}
