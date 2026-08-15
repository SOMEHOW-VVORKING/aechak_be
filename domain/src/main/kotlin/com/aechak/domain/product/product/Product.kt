package com.aechak.domain.product.product

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
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
    category: Category,
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    var category: Category = category
        protected set

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

    /** 닫힌 행까지 전부. 어느 버전에서 무엇이 보였는지가 여기 남음. */
    val images: List<ProductImage> get() = _images.toList()

    /** 지금 노출되는 행만. */
    val currentImages: List<ProductImage> get() = _images.filter { it.toVersionNo == null }

    /** sort_order는 image_type별로 매기므로 종류마다 0부터 다시 시작함. */
    private fun addImages(
        additionalImageKeys: List<String>,
        detailImageKeys: List<String>,
        fromVersionNo: Int,
    ) {
        representativeImageKey?.let { _images += ProductImage.of(ProductImageType.REPRESENTATIVE, it, 0, fromVersionNo) }
        additionalImageKeys.forEachIndexed { index, key ->
            _images += ProductImage.of(ProductImageType.PRODUCT, key, index, fromVersionNo)
        }
        detailImageKeys.forEachIndexed { index, key ->
            _images += ProductImage.of(ProductImageType.DETAIL, key, index, fromVersionNo)
        }
    }

    fun edit(
        category: Category,
        name: String,
        description: String?,
        regularPrice: Long,
        discountPrice: Long?,
        discountStartAt: LocalDateTime?,
        discountEndAt: LocalDateTime?,
    ) {
        validatePricing(regularPrice, discountPrice, discountStartAt, discountEndAt)
        this.category = category
        this.name = name
        this.description = description
        this.regularPrice = regularPrice
        this.discountPrice = discountPrice
        this.discountStartAt = discountStartAt
        this.discountEndAt = discountEndAt
    }

    /**
     * 보낸 목록이 곧 현재 노출분임. 빠진 행은 지우지 않고 직전 버전으로 닫아 노출 구간을 남김.
     * 순서는 행을 가르는 값이 아니라 열린 행에서 그 자리에 고침. 순서만 바뀌면 행이 늘지 않음.
     */
    fun replaceImages(
        thumbnailImageKey: String,
        additionalImageKeys: List<String>,
        detailImageKeys: List<String>,
        versionNo: Int,
    ) {
        validateImageCounts(additionalImageKeys, detailImageKeys)
        representativeImageKey = thumbnailImageKey
        val requested = imageOrders(thumbnailImageKey, additionalImageKeys, detailImageKeys)
        val (staying, dropped) = _images.filter { it.toVersionNo == null }.partition { slotOf(it) in requested }
        dropped.forEach { it.closeAt(versionNo - 1) }
        val stayingBySlot = staying.associateBy { slotOf(it) }
        requested.forEach { (slot, order) ->
            val (imageType, storageKey) = slot
            val existing = stayingBySlot[slot]
            if (existing == null) {
                _images += ProductImage.of(imageType, storageKey, order, versionNo)
            } else {
                existing.moveTo(order)
            }
        }
    }

    /** 종류와 키가 같으면 같은 이미지. 키가 업로드마다 새로 생기므로 내용이 바뀌면 여기가 갈림. */
    private fun slotOf(image: ProductImage): Pair<ProductImageType, String> = image.imageType to image.storageKey

    private fun imageOrders(
        thumbnailImageKey: String,
        additionalImageKeys: List<String>,
        detailImageKeys: List<String>,
    ): Map<Pair<ProductImageType, String>, Int> =
        buildMap {
            put(ProductImageType.REPRESENTATIVE to thumbnailImageKey, 0)
            additionalImageKeys.forEachIndexed { index, key -> put(ProductImageType.PRODUCT to key, index) }
            detailImageKeys.forEachIndexed { index, key -> put(ProductImageType.DETAIL to key, index) }
        }

    /** 셀러가 직접 바꾸는 경로. 재고에서 파생하는 전이는 여기로 들어오지 못함. */
    fun changeSaleStatusBySeller(status: SaleStatus) {
        if (!status.isSellerAssignable()) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "판매중과 판매중지만 지정할 수 있습니다.")
        }
        saleStatus = status
    }

    /** SUSPENDED와 ENDED는 재고와 무관하게 유지함. */
    fun syncSaleStatusWithStock(hasActiveStock: Boolean) {
        when {
            saleStatus == SaleStatus.ON_SALE && !hasActiveStock -> {
                saleStatus = SaleStatus.OUT_OF_STOCK
            }

            saleStatus == SaleStatus.OUT_OF_STOCK && hasActiveStock -> {
                saleStatus = SaleStatus.ON_SALE
            }

            else -> {
                Unit
            }
        }
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
        const val FIRST_VERSION_NO = 1

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
            validatePricing(regularPrice, discountPrice, discountStartAt, discountEndAt)
            validateImageCounts(additionalImageKeys, detailImageKeys)
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
            ).apply { addImages(additionalImageKeys, detailImageKeys, FIRST_VERSION_NO) }
        }

        fun validateEditable(
            regularPrice: Long,
            discountPrice: Long?,
            discountStartAt: LocalDateTime?,
            discountEndAt: LocalDateTime?,
            additionalImageKeys: List<String>,
            detailImageKeys: List<String>,
        ) {
            validatePricing(regularPrice, discountPrice, discountStartAt, discountEndAt)
            validateImageCounts(additionalImageKeys, detailImageKeys)
        }

        private fun validatePricing(
            regularPrice: Long,
            discountPrice: Long?,
            discountStartAt: LocalDateTime?,
            discountEndAt: LocalDateTime?,
        ) {
            if (regularPrice !in REGULAR_PRICE_MIN..REGULAR_PRICE_MAX) {
                throw BusinessException(ProductErrorCode.INVALID_PRODUCT_PRICE)
            }
            if (discountPrice != null && (discountPrice !in 0L..regularPrice)) {
                throw BusinessException(ProductErrorCode.INVALID_PRODUCT_PRICE)
            }
            // 할인가가 있으면 시작일 필수(종료일은 선택. 없으면 무기한). 상시할인은 불허.
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
        }

        private fun validateImageCounts(
            additionalImageKeys: List<String>,
            detailImageKeys: List<String>,
        ) {
            if (additionalImageKeys.size > ADDITIONAL_IMAGE_MAX || detailImageKeys.size > DETAIL_IMAGE_MAX) {
                throw BusinessException(ProductErrorCode.TOO_MANY_PRODUCT_IMAGES)
            }
        }
    }
}
