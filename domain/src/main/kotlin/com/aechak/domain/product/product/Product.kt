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
import jakarta.persistence.Index
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
    // 셀러센터 조회는 전부 "내 상품"으로 먼저 좁힌다.
    // products는 아직 Flyway 관리 밖(ddl-auto=update)이라 이 선언이 실제 인덱스를 만드는 곳은
    // 개발·테스트 스키마뿐이다 — ddl-auto=none인 운영에는 products DDL 이관 마이그레이션이
    // 같은 이름으로 넣어야 붙는다. 조건부 ALTER를 V59에 넣었다가 뺐다: products가 없는 분기가
    // 어느 테스트에서도 실행되지 않아 검증되지 않는 코드가 된다.
    indexes = [
        Index(name = "ix_products_seller_id", columnList = "seller_id, id"),
        // 검색 인기순과 낮은 가격순을 인덱스 순서로 읽는다. 운영에는 마이그레이션이 같은 이름으로 만든다.
        Index(name = "ix_products_popular", columnList = "review_count DESC, id DESC"),
        Index(name = "ix_products_discount_price", columnList = "discount_price, id DESC"),
        Index(name = "ix_products_regular_price", columnList = "regular_price, id DESC"),
    ],
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

    /**
     * 운영자가 판매를 막은 시각. null이면 막혀 있지 않다 — 이 한 칸이 "지금 운영자가 막았는가"의 축이다.
     * saleStatus로 대신할 수 없다: SUSPENDED는 셀러도 지정할 수 있는 값이라 표시 없이 상태만 옮기면
     * 셀러가 자기 경로로 즉시 되돌린다.
     */
    @Column
    var adminSaleBlockedAt: LocalDateTime? = null
        protected set

    /** 막은 사유. 셀러에게 보내는 문장이 아니라 운영 기록이다. */
    @Column(length = ADMIN_SALE_BLOCK_REASON_MAX)
    var adminSaleBlockReason: String? = null
        protected set

    @Column
    var adminSaleBlockedByAdminId: Long? = null
        protected set

    /** 막기 직전의 판매 상태. 재개가 되돌릴 자리다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var adminSaleBlockPreviousStatus: SaleStatus? = null
        protected set

    /** 검색 인기순 정렬용 리뷰 수. 리뷰 통계 반영이 네이티브 UPDATE로만 쓰고 Hibernate는 쓰지 않는다 */
    @Column(nullable = false, insertable = false, updatable = false, columnDefinition = "INT DEFAULT 0")
    val reviewCount: Int = 0

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
        val current = currentImages
        val currentOrders = current.associate { slotOf(it) to it.sortOrder }
        val (staying, dropped) = current.partition { slotOf(it) in requested }
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
        if (currentOrders != requested) refreshUpdatedAt()
    }

    private fun refreshUpdatedAt() {
        updatedAt = LocalDateTime.now()
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

    /**
     * 셀러가 직접 바꾸는 경로. 재고에서 파생하는 전이는 여기로 들어오지 못함.
     *
     * 운영자가 막아 둔 상품은 거부한다. SUSPENDED가 셀러 지정 가능 값이라 이 가드가 없으면
     * 운영자의 판매중지가 셀러의 다음 요청 한 번에 뒤집힌다.
     *
     * 검수에서 걸린 상품은 판매중으로 올리는 것만 거부한다. 걸렸다는 것은 검수 반려이거나
     * 관리자 검토를 기다리는 중([awaitingInspectionReview])이라는 뜻이다. 판매중지로 내리는 것은 받는다.
     */
    fun changeSaleStatusBySeller(
        status: SaleStatus,
        awaitingInspectionReview: Boolean,
    ) {
        if (adminSaleBlocked) {
            throw BusinessException(ProductErrorCode.PRODUCT_SALE_BLOCKED_BY_ADMIN)
        }
        if (!status.isSellerAssignable()) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "판매중과 판매중지만 지정할 수 있습니다.")
        }
        if (status == SaleStatus.ON_SALE && (inspectionStatus == InspectionStatus.REJECTED || awaitingInspectionReview)) {
            throw BusinessException(ProductErrorCode.PRODUCT_SALE_BLOCKED_BY_INSPECTION)
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

    /**
     * 검수에서 위반으로 판정되어 노출을 멈춘다.
     *
     * 판매 상태는 건드리지 않는다. 셀러가 판매중으로 둔 의사와 플랫폼이 노출을 막은 사실은
     * 다른 축이고, 함께 바꾸면 되돌릴 때 셀러의 의사를 복원할 방법이 없어진다.
     */
    fun rejectByInspection() {
        inspectionStatus = InspectionStatus.REJECTED
    }

    /** 재검수가 통과해 노출을 되돌린다. */
    fun approveByInspection() {
        inspectionStatus = InspectionStatus.APPROVED
    }

    /** 운영자가 막아 둔 상품인지. 셀러의 상태 변경을 거부하는 조건이자 목록의 표시 값. */
    val adminSaleBlocked: Boolean get() = adminSaleBlockedAt != null

    /**
     * 운영자가 판매를 막는다. 판매 상태를 SUSPENDED로 옮기고 막음 표시를 남긴다.
     *
     * 따로 가릴 수단을 두지 않는 이유: SUSPENDED는 노출 화이트리스트(SaleStatus.EXPOSABLE)와
     * 구매·주문 판정(canPurchase·canOrder)에서 모두 빠져 목록·검색·찜·상세·옵션·주문이 한꺼번에 끊긴다.
     */
    fun blockSaleByAdmin(
        adminId: Long,
        reason: String,
    ) {
        if (adminSaleBlocked) {
            throw BusinessException(ProductErrorCode.PRODUCT_SALE_ALREADY_BLOCKED_BY_ADMIN)
        }
        adminSaleBlockPreviousStatus = saleStatus
        saleStatus = SaleStatus.SUSPENDED
        adminSaleBlockedAt = LocalDateTime.now()
        adminSaleBlockReason = reason
        adminSaleBlockedByAdminId = adminId
    }

    /**
     * 운영자가 판매를 되돌린다. 막기 직전 상태로 돌리고 표시를 지운다.
     *
     * 일괄로 ON_SALE로 올리지 않는다 — 스스로 판매중지해 둔 셀러의 의사까지 운영자 조치가 뒤집는다.
     * 직전 상태가 비어 있는 행은 이 전이가 생기기 전에 막힌 상품이라 ON_SALE로 되돌린다.
     * 품절은 재고에서 파생하는 값이므로 호출측이 syncSaleStatusWithStock을 이어 불러 맞춘다.
     */
    fun unblockSaleByAdmin() {
        if (!adminSaleBlocked) {
            throw BusinessException(ProductErrorCode.PRODUCT_SALE_NOT_BLOCKED_BY_ADMIN)
        }
        saleStatus = adminSaleBlockPreviousStatus ?: SaleStatus.ON_SALE
        adminSaleBlockPreviousStatus = null
        adminSaleBlockedAt = null
        adminSaleBlockReason = null
        adminSaleBlockedByAdminId = null
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

        /** admin_sale_block_reason 컬럼 길이. 요청 DTO의 상한이 이 상수를 참조한다. */
        const val ADMIN_SALE_BLOCK_REASON_MAX = 500

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
