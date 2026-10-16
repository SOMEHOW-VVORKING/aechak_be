package com.aechak.domain.product.stats

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "product_stats")
class ProductStats protected constructor(
    @Id
    val productId: Long,
) : AggregateRoot() {
    // 카운터·평점 집계는 저장소 조건부 원자 UPDATE로만 갱신, 쿼리에 updated_at = NOW() 동반 SET.
    var reviewCount: Int = 0
        protected set

    var ratingSum: Long = 0L
        protected set

    @Column(precision = 3, scale = 2)
    var averageRating: BigDecimal? = null
        protected set

    var likeCount: Long = 0L
        protected set

    companion object {
        fun create(productId: Long): ProductStats = ProductStats(productId)
    }
}
