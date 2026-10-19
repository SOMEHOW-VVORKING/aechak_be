package com.aechak.domain.product.draft

import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * 상품 등록 폼의 임시저장본. 상품(Product)과 달리 전 필드가 비어 있어도 되고 불변식도 없다 —
 * 필수값·가격·옵션 구성 검증은 정식 등록 시점에 Product가 건다.
 *
 * payload는 등록 폼의 중첩 입력(이미지 키 목록·옵션)을 담은 JSON이다. 서버는 내용을 해석하지 않고
 * 셀러가 보낸 모양 그대로 보관한다.
 */
@Entity
@Table(
    name = "product_drafts",
    uniqueConstraints = [UniqueConstraint(name = "uk_product_draft_public_id", columnNames = ["public_id"])],
    indexes = [Index(name = "ix_product_draft_seller", columnList = "seller_id, updated_at")],
)
class ProductDraft protected constructor(
    @Column(name = "seller_id", nullable = false)
    val sellerId: Long,
    categoryId: Long?,
    productName: String?,
    description: String?,
    regularPrice: Long?,
    discountPrice: Long?,
    discountStartAt: LocalDateTime?,
    discountEndAt: LocalDateTime?,
    thumbnailImageKey: String?,
    payload: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false, length = 26)
    val publicId: String = Ulid.generate()

    @Column(name = "category_id")
    var categoryId: Long? = categoryId
        protected set

    @Column(name = "product_name", length = NAME_MAX)
    var productName: String? = productName
        protected set

    @Column(columnDefinition = "TEXT")
    var description: String? = description
        protected set

    var regularPrice: Long? = regularPrice
        protected set

    var discountPrice: Long? = discountPrice
        protected set

    var discountStartAt: LocalDateTime? = discountStartAt
        protected set

    var discountEndAt: LocalDateTime? = discountEndAt
        protected set

    @Column(length = IMAGE_KEY_MAX)
    var thumbnailImageKey: String? = thumbnailImageKey
        protected set

    @Column(nullable = false, columnDefinition = "json")
    var payload: String = payload
        protected set

    /**
     * 폼 내용을 통째로 덮어쓴다. 빠진 필드는 비우는 것이 셀러의 의도이므로 null도 그대로 반영한다.
     * updatedAt을 직접 올려 내용이 같은 재저장도 저장 시각이 움직이게 한다.
     */
    fun overwrite(
        categoryId: Long?,
        productName: String?,
        description: String?,
        regularPrice: Long?,
        discountPrice: Long?,
        discountStartAt: LocalDateTime?,
        discountEndAt: LocalDateTime?,
        thumbnailImageKey: String?,
        payload: String,
    ) {
        this.categoryId = categoryId
        this.productName = productName
        this.description = description
        this.regularPrice = regularPrice
        this.discountPrice = discountPrice
        this.discountStartAt = discountStartAt
        this.discountEndAt = discountEndAt
        this.thumbnailImageKey = thumbnailImageKey
        this.payload = payload
        updatedAt = LocalDateTime.now()
    }

    companion object {
        const val NAME_MAX = 255
        const val IMAGE_KEY_MAX = 1024

        fun of(
            sellerId: Long,
            categoryId: Long?,
            productName: String?,
            description: String?,
            regularPrice: Long?,
            discountPrice: Long?,
            discountStartAt: LocalDateTime?,
            discountEndAt: LocalDateTime?,
            thumbnailImageKey: String?,
            payload: String,
        ): ProductDraft =
            ProductDraft(
                sellerId = sellerId,
                categoryId = categoryId,
                productName = productName,
                description = description,
                regularPrice = regularPrice,
                discountPrice = discountPrice,
                discountStartAt = discountStartAt,
                discountEndAt = discountEndAt,
                thumbnailImageKey = thumbnailImageKey,
                payload = payload,
            )
    }
}
