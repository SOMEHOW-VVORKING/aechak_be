package com.aechak.application.user.pet.facade

import com.aechak.application.user.pet.service.BreedService
import com.aechak.application.user.pet.usecase.BreedUseCase
import com.aechak.application.user.pet.usecase.result.BreedResult
import com.aechak.domain.user.pet.enums.Species
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BreedFacade(
    private val breedService: BreedService,
) : BreedUseCase {
    @Transactional(readOnly = true)
    override fun getBreeds(species: Species): List<BreedResult> = breedService.getBySpecies(species).map(BreedResult::from)
}
