package com.aechak.domain.review.review

import com.aechak.domain.review.summary.PetWeightBand
import com.aechak.domain.user.pet.enums.Species
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.math.BigDecimal

/** 리뷰 작성 시점의 작성자 대표 펫. 펫 정보가 나중에 바뀌어도 리뷰가 쓰인 조건은 그대로 남김 */
@Embeddable
class ReviewPetSnapshot(
    @Enumerated(EnumType.STRING)
    @Column(name = "pet_species", length = 10)
    val species: Species,
    @Column(name = "pet_breed_id")
    val breedId: Long,
    @Column(name = "pet_birth_year_month", length = 7)
    val birthYearMonth: String?,
    @Column(name = "pet_weight", precision = 4, scale = 1)
    val weight: BigDecimal?,
    @Column(name = "pet_segment_key", length = 30)
    val segmentKey: String?,
) {
    companion object {
        fun of(
            species: Species,
            breedId: Long,
            birthYearMonth: String?,
            weight: BigDecimal?,
        ): ReviewPetSnapshot = ReviewPetSnapshot(species, breedId, birthYearMonth, weight, PetWeightBand.of(species, weight)?.segmentKey)
    }
}
