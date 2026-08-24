package com.aechak.infra.persistence.product

import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.option.OptionGroup
import com.aechak.domain.product.option.repository.OptionCombinationRepository
import com.aechak.domain.product.option.repository.OptionGroupRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface OptionGroupJpaRepository : JpaRepository<OptionGroup, Long>

interface OptionCombinationJpaRepository : JpaRepository<OptionCombination, Long> {
    @Modifying
    @Query(
        "update OptionCombination oc " +
            "set oc.stockQuantity = oc.stockQuantity - :quantity, oc.updatedAt = :now " +
            "where oc.id = :id and oc.stockQuantity >= :quantity",
    )
    fun deductStock(
        @Param("id") id: Long,
        @Param("quantity") quantity: Int,
        @Param("now") now: LocalDateTime,
    ): Int

    @Query(
        "select count(c) > 0 from OptionCombination c " +
            "where c.product.id = :productId and c.isActive = true and c.stockQuantity > 0",
    )
    fun existsActiveStock(
        @Param("productId") productId: Long,
    ): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OptionCombination c where c.id = :id and c.product.id = :productId")
    fun findByIdAndProductIdForUpdate(
        @Param("id") id: Long,
        @Param("productId") productId: Long,
    ): OptionCombination?
}

@Repository
class OptionGroupRepositoryAdapter(
    private val jpaRepository: OptionGroupJpaRepository,
) : OptionGroupRepository {
    override fun saveAll(optionGroups: List<OptionGroup>): List<OptionGroup> = jpaRepository.saveAll(optionGroups)
}

@Repository
class OptionCombinationRepositoryAdapter(
    private val jpaRepository: OptionCombinationJpaRepository,
) : OptionCombinationRepository {
    override fun saveAll(optionCombinations: List<OptionCombination>): List<OptionCombination> = jpaRepository.saveAll(optionCombinations)

    override fun saveNow(optionCombination: OptionCombination): OptionCombination = jpaRepository.saveAndFlush(optionCombination)

    override fun findById(id: Long): OptionCombination? = jpaRepository.findByIdOrNull(id)

    override fun findByIdAndProductIdForUpdate(
        id: Long,
        productId: Long,
    ): OptionCombination? = jpaRepository.findByIdAndProductIdForUpdate(id, productId)

    override fun existsActiveStock(productId: Long): Boolean = jpaRepository.existsActiveStock(productId)

    // 벌크 JPQL은 @PreUpdate를 우회하므로 updated_at을 쿼리에서 함께 SET한다
    override fun deductStock(
        optionCombinationId: Long,
        quantity: Int,
    ): Boolean = jpaRepository.deductStock(optionCombinationId, quantity, LocalDateTime.now()) == 1
}
