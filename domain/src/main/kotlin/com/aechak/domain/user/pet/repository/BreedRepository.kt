package com.aechak.domain.user.pet.repository

import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.enums.Species

interface BreedRepository {
    fun findAllBySpecies(species: Species): List<Breed>
}
