package com.aechak.domain.product.like

import com.aechak.domain.product.product.Product
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "product_likes",
    uniqueConstraints = [UniqueConstraint(name = "uk_product_like", columnNames = ["product_id", "user_id"])],
)
class ProductLike protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
    @Column(name = "user_id")
    val userId: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    companion object {
        fun of(
            product: Product,
            userId: Long,
        ): ProductLike = ProductLike(product, userId)
    }
}
