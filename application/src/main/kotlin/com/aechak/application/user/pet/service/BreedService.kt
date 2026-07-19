package com.aechak.application.user.pet.service

import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.enums.Species
import com.aechak.domain.user.pet.repository.BreedRepository
import org.springframework.stereotype.Service

@Service
class BreedService(
    private val breedRepository: BreedRepository,
) {
    fun getBySpecies(species: Species): List<Breed> = breedRepository.findAllBySpeciesOrderedById(species)
}
