package com.aechak.infra.persistence.product.search

import org.hibernate.boot.model.FunctionContributions
import org.hibernate.boot.model.FunctionContributor
import org.hibernate.type.StandardBasicTypes

/** MySQL FULLTEXT 불리언 모드 관련도 match_against(column, query)를 HQL 함수로 등록한다 */
class MatchAgainstFunctionContributor : FunctionContributor {
    override fun contributeFunctions(functionContributions: FunctionContributions) {
        functionContributions.functionRegistry.registerPattern(
            "match_against",
            "match(?1) against (?2 in boolean mode)",
            functionContributions.typeConfiguration.basicTypeRegistry.resolve(StandardBasicTypes.DOUBLE),
        )
    }
}
