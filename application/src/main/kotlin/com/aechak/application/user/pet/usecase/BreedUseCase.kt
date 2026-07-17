package com.aechak.application.user.pet.usecase

import com.aechak.application.user.pet.usecase.result.BreedResult
import com.aechak.domain.user.pet.enums.Species

interface BreedUseCase {
    fun getBreeds(species: Species): List<BreedResult>
}
