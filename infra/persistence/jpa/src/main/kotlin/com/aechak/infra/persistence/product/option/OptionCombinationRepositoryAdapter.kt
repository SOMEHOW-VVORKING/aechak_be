package com.aechak.infra.persistence.product.option

import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.option.repository.OptionCombinationRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

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
}

@Repository
class OptionCombinationRepositoryAdapter(
    private val jpaRepository: OptionCombinationJpaRepository,
) : OptionCombinationRepository {
    // 벌크 JPQL은 @PreUpdate를 우회하므로 updated_at을 쿼리에서 함께 SET한다
    override fun deductStock(
        optionCombinationId: Long,
        quantity: Int,
    ): Boolean = jpaRepository.deductStock(optionCombinationId, quantity, LocalDateTime.now()) == 1
}
