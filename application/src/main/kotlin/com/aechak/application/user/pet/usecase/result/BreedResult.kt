package com.aechak.application.user.pet.usecase.result

import com.aechak.domain.user.pet.Breed

data class BreedResult(
    val breedId: Long,
    val label: String,
) {
    companion object {
        fun from(breed: Breed): BreedResult = BreedResult(breed.id, breed.label)
    }
}
