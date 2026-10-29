package com.aechak.domain.review.summary

import com.aechak.domain.user.pet.enums.Species
import java.math.BigDecimal

/** 리뷰 요약을 비슷한 아이끼리 묶는 체급. 경계는 AI 서버 selection.py의 WeightBand와 같아야 함 */
enum class PetWeightBand(
    val species: Species,
    private val upperBoundKgExclusive: BigDecimal?,
) {
    DOG_UNDER_5(Species.DOG, BigDecimal(5)),
    DOG_5_10(Species.DOG, BigDecimal(10)),
    DOG_10_25(Species.DOG, BigDecimal(25)),
    DOG_25_PLUS(Species.DOG, null),
    CAT_UNDER_4(Species.CAT, BigDecimal(4)),
    CAT_4_6(Species.CAT, BigDecimal(6)),
    CAT_6_PLUS(Species.CAT, null),
    ;

    val segmentKey: String get() = "${species.name}:$name"

    companion object {
        // 체중을 모르면 묶지 않음. 체중 모르는 아이끼리를 비슷한 아이로 보지 않기 때문
        fun of(
            species: Species,
            weight: BigDecimal?,
        ): PetWeightBand? {
            if (weight == null || weight.signum() <= 0) return null
            return entries.first { it.species == species && (it.upperBoundKgExclusive == null || weight < it.upperBoundKgExclusive) }
        }

        fun findBySegmentKey(segmentKey: String): PetWeightBand? = entries.firstOrNull { it.segmentKey == segmentKey }
    }
}
