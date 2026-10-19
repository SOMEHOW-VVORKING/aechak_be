package com.aechak.infra.persistence.product.draft

import com.aechak.domain.product.draft.ProductDraft
import com.aechak.domain.product.draft.repository.ProductDraftRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface ProductDraftJpaRepository : JpaRepository<ProductDraft, Long> {
    @Query("select d from ProductDraft d where d.publicId = :publicId and d.sellerId = :sellerId and d.updatedAt >= :savedAfter")
    fun findLive(
        @Param("publicId") publicId: String,
        @Param("sellerId") sellerId: Long,
        @Param("savedAfter") savedAfter: LocalDateTime,
    ): ProductDraft?

    @Query("select d from ProductDraft d where d.sellerId = :sellerId and d.updatedAt >= :savedAfter order by d.updatedAt desc, d.id desc")
    fun findAllLive(
        @Param("sellerId") sellerId: Long,
        @Param("savedAfter") savedAfter: LocalDateTime,
    ): List<ProductDraft>

    @Query("select count(d) from ProductDraft d where d.sellerId = :sellerId and d.updatedAt >= :savedAfter")
    fun countLive(
        @Param("sellerId") sellerId: Long,
        @Param("savedAfter") savedAfter: LocalDateTime,
    ): Long

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProductDraft d where d.publicId = :publicId and d.sellerId = :sellerId and d.updatedAt >= :savedAfter")
    fun deleteLive(
        @Param("publicId") publicId: String,
        @Param("sellerId") sellerId: Long,
        @Param("savedAfter") savedAfter: LocalDateTime,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProductDraft d where d.sellerId = :sellerId and d.updatedAt < :savedBefore")
    fun deleteExpired(
        @Param("sellerId") sellerId: Long,
        @Param("savedBefore") savedBefore: LocalDateTime,
    ): Int
}

@Repository
class ProductDraftRepositoryAdapter(
    private val jpaRepository: ProductDraftJpaRepository,
) : ProductDraftRepository {
    override fun save(draft: ProductDraft): ProductDraft = jpaRepository.save(draft)

    override fun findLive(
        publicId: String,
        sellerId: Long,
        savedAfter: LocalDateTime,
    ): ProductDraft? = jpaRepository.findLive(publicId, sellerId, savedAfter)

    override fun findAllLive(
        sellerId: Long,
        savedAfter: LocalDateTime,
    ): List<ProductDraft> = jpaRepository.findAllLive(sellerId, savedAfter)

    override fun countLive(
        sellerId: Long,
        savedAfter: LocalDateTime,
    ): Long = jpaRepository.countLive(sellerId, savedAfter)

    override fun deleteLive(
        publicId: String,
        sellerId: Long,
        savedAfter: LocalDateTime,
    ): Int = jpaRepository.deleteLive(publicId, sellerId, savedAfter)

    override fun deleteExpired(
        sellerId: Long,
        savedBefore: LocalDateTime,
    ): Int = jpaRepository.deleteExpired(sellerId, savedBefore)
}
