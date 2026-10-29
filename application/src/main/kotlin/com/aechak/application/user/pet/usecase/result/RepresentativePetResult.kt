package com.aechak.application.user.pet.usecase.result

import com.aechak.domain.user.pet.PetProfile
import com.aechak.domain.user.pet.enums.Species
import java.math.BigDecimal

data class RepresentativePetResult(
    val species: Species,
    val breedId: Long,
    val birthYearMonth: String?,
    val weight: BigDecimal?,
) {
    companion object {
        fun from(pet: PetProfile): RepresentativePetResult =
            RepresentativePetResult(pet.species, pet.breed.id, pet.birthYearMonth, pet.weight)
    }
}
