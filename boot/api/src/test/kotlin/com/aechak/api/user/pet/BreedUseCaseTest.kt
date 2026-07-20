package com.aechak.api.user.pet

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.user.pet.usecase.BreedUseCase
import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.enums.Species
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class BreedUseCaseTest : IntegrationTestBase() {
    @Autowired
    lateinit var breedUseCase: BreedUseCase

    @Test
    fun `요청한 종의 품종만 id 순으로 반환한다`() {
        val dogLabels = listOf("골든 리트리버", "(세상에 하나뿐인) 믹스", "기타")
        tx.executeWithoutResult {
            dogLabels.forEach { em.persist(Breed.of(Species.DOG, it)) }
            em.persist(Breed.of(Species.CAT, "코리안 숏헤어"))
        }

        val dogs = breedUseCase.getBreeds(Species.DOG)
        assertEquals(dogLabels, dogs.map { it.label }, "DOG만, 삽입(id) 순서 그대로 — sentinel 2종 포함")

        val cats = breedUseCase.getBreeds(Species.CAT)
        assertEquals(listOf("코리안 숏헤어"), cats.map { it.label })
    }

    @Test
    fun `해당 종의 품종이 없으면 예외가 아닌 빈 목록을 반환한다`() {
        assertEquals(emptyList<Any>(), breedUseCase.getBreeds(Species.CAT))
    }
}
