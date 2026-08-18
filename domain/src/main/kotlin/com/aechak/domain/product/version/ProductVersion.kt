package com.aechak.domain.product.version

import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.enums.VersionChangeType
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.support.AggregateRoot
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
import jakarta.persistence.Table

@Entity
@Table(name = "product_versions")
class ProductVersion protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
    val versionNo: Int,
    @Column(length = 255, nullable = false)
    val nameSnapshot: String,
    val priceSnapshot: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val statusSnapshot: SaleStatus,
    @Column(length = 1024, nullable = false)
    val thumbnailKeySnapshot: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val changeType: VersionChangeType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val changedBy: VersionChangedBy,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    companion object {
        fun create(
            product: Product,
            thumbnailKeySnapshot: String,
        ): ProductVersion =
            snapshot(
                product = product,
                versionNo = 1,
                nameSnapshot = product.name,
                priceSnapshot = product.regularPrice,
                statusSnapshot = product.saleStatus,
                thumbnailKeySnapshot = thumbnailKeySnapshot,
                changeType = VersionChangeType.INFO,
                changedBy = VersionChangedBy.SELLER,
            )

        fun snapshot(
            product: Product,
            versionNo: Int,
            nameSnapshot: String,
            priceSnapshot: Long,
            statusSnapshot: SaleStatus,
            thumbnailKeySnapshot: String,
            changeType: VersionChangeType,
            changedBy: VersionChangedBy,
        ): ProductVersion =
            ProductVersion(
                product,
                versionNo,
                nameSnapshot,
                priceSnapshot,
                statusSnapshot,
                thumbnailKeySnapshot,
                changeType,
                changedBy,
            )
    }
}
