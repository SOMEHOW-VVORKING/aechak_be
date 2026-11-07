package com.aechak.infra.elasticsearch.product.search

import com.aechak.application.product.search.port.ProductSearchDocument
import java.time.LocalDateTime
import java.time.ZoneOffset

/** 상품 검색 색인 이름과 문서 변환. 시각은 UTC 기준 epoch 마이크로초로 저장하고 검색할 때도 같은 변환을 씀 */
internal object ProductSearchIndex {
    const val NAME = "products"
    const val MAPPING_RESOURCE = "elasticsearch/products-index.json"

    fun toSource(document: ProductSearchDocument): Map<String, Any?> =
        with(document) {
            mapOf(
                "id" to id,
                "public_id" to publicId,
                "name" to name,
                "category_id" to categoryId,
                "seller_id" to sellerId,
                "inspection_status" to inspectionStatus.name,
                "sale_status" to saleStatus.name,
                "seller_status" to sellerStatus?.name,
                "seller_base_shipping_fee" to sellerBaseShippingFee,
                "regular_price" to regularPrice,
                "discount_price" to discountPrice,
                "discount_start_at" to discountStartAt?.let(::epochMicros),
                "discount_end_at" to discountEndAt?.let(::epochMicros),
                "review_count" to reviewCount,
                "stats_review_count" to statsReviewCount,
                // 문자열로 보내야 _source가 소수 자릿수(3.50)를 그대로 보관함
                "average_rating" to averageRating?.toPlainString(),
                "store_name" to storeName,
                "representative_image_key" to representativeImageKey,
            )
        }

    fun epochMicros(time: LocalDateTime): Long = time.toEpochSecond(ZoneOffset.UTC) * 1_000_000 + time.nano / 1_000

    fun fromEpochMicros(micros: Long): LocalDateTime =
        LocalDateTime.ofEpochSecond(Math.floorDiv(micros, 1_000_000), Math.floorMod(micros, 1_000_000).toInt() * 1_000, ZoneOffset.UTC)
}
