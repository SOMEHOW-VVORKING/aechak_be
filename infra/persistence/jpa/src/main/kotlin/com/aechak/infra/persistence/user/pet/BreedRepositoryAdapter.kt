package com.aechak.infra.persistence.user.pet

import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.enums.Species
import com.aechak.domain.user.pet.repository.BreedRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface BreedJpaRepository : JpaRepository<Breed, Long> {
    fun findAllBySpeciesOrderByIdAsc(species: Species): List<Breed>
}

@Repository
class BreedRepositoryAdapter(
    private val jpaRepository: BreedJpaRepository,
) : BreedRepository {
    override fun findAllBySpecies(species: Species): List<Breed> = jpaRepository.findAllBySpeciesOrderByIdAsc(species)
}
