package com.aechak.infra.persistence.product

import com.aechak.application.product.product.port.ProductOptionsQueryPort
import com.aechak.application.product.product.port.view.ProductOptionsView
import com.aechak.domain.product.option.QOptionCombination
import com.aechak.domain.product.option.QOptionCombinationValue
import com.aechak.domain.product.option.QOptionGroup
import com.aechak.domain.product.option.QOptionValue
import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val optionGroup = QOptionGroup.optionGroup
private val optionValue = QOptionValue.optionValue
private val optionCombination = QOptionCombination.optionCombination
private val combinationValue = QOptionCombinationValue.optionCombinationValue

/** 공개 상품 옵션 조회 모델 */
@Repository
class ProductOptionsQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : ProductOptionsQueryPort {
    override fun findVisibleOptions(publicId: String): ProductOptionsView? {
        val productId =
            visibleProductQuery(queryFactory, product.id)
                .where(product.publicId.eq(publicId))
                .fetchOne() ?: return null

        return ProductOptionsView(
            optionGroups = fetchActiveGroups(productId),
            optionCombinations = fetchSelectableCombinations(productId),
        )
    }

    /** 활성 그룹과 활성 값을 조회, 그룹별로 조립
     * 값 없는 그룹도 포함하여 빈 값으로 반환 */
    private fun fetchActiveGroups(productId: Long): List<ProductOptionsView.OptionGroupView> {
        val rows =
            queryFactory
                .select(
                    optionGroup.id,
                    optionGroup.name,
                    optionGroup.sortOrder,
                    optionValue.id,
                    optionValue.name,
                    optionValue.sortOrder,
                ).from(optionGroup)
                .leftJoin(optionGroup._values, optionValue)
                .on(optionValue.isActive.isTrue)
                .where(optionGroup.product.id.eq(productId), optionGroup.isActive.isTrue)
                .fetch()

        return rows
            .groupBy { it.get(optionGroup.id)!! }
            .map { (groupId, groupRows) -> toGroupView(groupId, groupRows) }
            .sortedWith(compareBy({ it.sortOrder }, { it.optionGroupId }))
    }

    private fun toGroupView(
        groupId: Long,
        groupRows: List<Tuple>,
    ): ProductOptionsView.OptionGroupView {
        val first = groupRows.first()
        return ProductOptionsView.OptionGroupView(
            optionGroupId = groupId,
            name = first.get(optionGroup.name)!!,
            sortOrder = first.get(optionGroup.sortOrder)!!,
            values =
                groupRows
                    .mapNotNull { row ->
                        row.get(optionValue.id)?.let { valueId ->
                            ProductOptionsView.OptionValueView(
                                optionValueId = valueId,
                                name = row.get(optionValue.name)!!,
                                sortOrder = row.get(optionValue.sortOrder)!!,
                            )
                        }
                    }.sortedWith(compareBy({ it.sortOrder }, { it.optionValueId })),
        )
    }

    /**
     * 활성 조합을 조회
     * 그룹 목록에 노출되지 않는 값(비활성 값, 비활성 그룹 소속)을 참조하는 조합은 제외
     */
    private fun fetchSelectableCombinations(productId: Long): List<ProductOptionsView.OptionCombinationView> {
        val combinationRows =
            queryFactory
                .select(
                    optionCombination.id,
                    optionCombination.name,
                    optionCombination.additionalPrice,
                    optionCombination.stockQuantity,
                ).from(optionCombination)
                .where(optionCombination.product.id.eq(productId), optionCombination.isActive.isTrue)
                .fetch()

        val valueIdsByCombination =
            queryFactory
                .select(optionCombination.id, combinationValue.optionValue.id)
                .from(optionCombination)
                .join(optionCombination._values, combinationValue)
                .where(optionCombination.product.id.eq(productId), optionCombination.isActive.isTrue)
                .fetch()
                .groupBy({ it.get(optionCombination.id)!! }, { it.get(combinationValue.optionValue.id)!! })

        val exposedValueIds =
            queryFactory
                .select(optionValue.id)
                .from(optionGroup)
                .join(optionGroup._values, optionValue)
                .where(optionGroup.product.id.eq(productId), optionGroup.isActive.isTrue, optionValue.isActive.isTrue)
                .fetch()
                .toSet()

        return combinationRows
            .map { row ->
                val combinationId = row.get(optionCombination.id)!!
                ProductOptionsView.OptionCombinationView(
                    optionCombinationId = combinationId,
                    name = row.get(optionCombination.name)!!,
                    additionalPrice = row.get(optionCombination.additionalPrice)!!,
                    optionValueIds = valueIdsByCombination[combinationId].orEmpty().sorted(),
                    stockQuantity = row.get(optionCombination.stockQuantity)!!,
                )
            }.filter { combination -> exposedValueIds.containsAll(combination.optionValueIds) }
            .sortedBy { it.optionCombinationId }
    }
}
