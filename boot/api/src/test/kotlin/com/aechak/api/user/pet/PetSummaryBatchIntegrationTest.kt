package com.aechak.api.user.pet

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.PetProfile
import com.aechak.domain.user.pet.enums.Species
import com.aechak.domain.user.user.User
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 통합: 목록 행에 붙일 펫 요약을 여러 사용자에 대해 한 번에 읽을 때 대표 펫을 isDefault로 고르고(없으면 먼저 등록한 펫) ACTIVE 펫만 세는지.
 * 깨지면 목록 행의 품종이 대표 펫이 아니거나 펫이 있는 사용자의 펫 줄이 빠지거나 삭제한 펫까지 품종 외 N에 들어간다.
 */
class PetSummaryBatchIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var petProfileUseCase: PetProfileUseCase

    @Test
    fun `대표 펫의 품종과 생년월을 싣고 ACTIVE 펫만 센다`() {
        val owner = createActiveUser()
        tx.execute {
            val user = em.find(User::class.java, owner)
            val maltese = Breed.of(Species.DOG, "Maltese").also { em.persist(it) }
            val poodle = Breed.of(Species.DOG, "Poodle").also { em.persist(it) }
            em.persist(PetProfile.register(user, maltese, "first"))
            em.persist(PetProfile.register(user, poodle, "main", birthYearMonth = "2021-03", isDefault = true))
            em.persist(PetProfile.register(user, maltese, "gone").also { it.delete() })
        }

        val summaries = petProfileUseCase.getDefaultPetSummaries(listOf(owner))

        assertEquals(1, summaries.size, "펫이 있는 사용자 한 명의 요약만 있어야 한다")
        val summary = summaries.single()
        assertEquals(owner, summary.userId, "요약은 요청한 사용자의 것이어야 한다")
        assertEquals("Poodle", summary.breedLabel, "대표 펫(isDefault)의 품종이어야 한다")
        assertEquals("2021-03", summary.birthYearMonth, "대표 펫의 생년월이어야 한다")
        assertEquals(2, summary.activePetCount, "삭제한 펫을 빼고 ACTIVE 펫만 세야 한다")
    }

    @Test
    fun `대표 펫이 없으면 먼저 등록한 펫으로 요약하고 ACTIVE 펫을 모두 센다`() {
        val owner = createActiveUser()
        tx.execute {
            val user = em.find(User::class.java, owner)
            val maltese = Breed.of(Species.DOG, "Maltese").also { em.persist(it) }
            val poodle = Breed.of(Species.DOG, "Poodle").also { em.persist(it) }
            em.persist(PetProfile.register(user, poodle, "older", birthYearMonth = "2020-01"))
            em.persist(PetProfile.register(user, maltese, "newer"))
        }

        val summary = petProfileUseCase.getDefaultPetSummaries(listOf(owner)).single()

        assertEquals("Poodle", summary.breedLabel, "대표 펫이 없으면 먼저 등록한 펫의 품종이어야 한다")
        assertEquals("2020-01", summary.birthYearMonth, "먼저 등록한 펫의 생년월이어야 한다")
        assertEquals(2, summary.activePetCount, "대표 펫이 없어도 ACTIVE 펫을 모두 세야 한다")
    }

    @Test
    fun `펫이 없거나 삭제한 펫만 있는 사용자는 요약이 없다`() {
        val noPet = createActiveUser()
        val onlyDeleted = createActiveUser()
        val withPet = createActiveUser()
        tx.execute {
            val breed = Breed.of(Species.CAT, "Korean").also { em.persist(it) }
            em.persist(PetProfile.register(em.find(User::class.java, onlyDeleted), breed, "gone").also { it.delete() })
            em.persist(PetProfile.register(em.find(User::class.java, withPet), breed, "cat", isDefault = true))
        }

        val summaries = petProfileUseCase.getDefaultPetSummaries(listOf(noPet, onlyDeleted, withPet))

        assertEquals(listOf(withPet), summaries.map { it.userId }, "ACTIVE 펫이 있는 사용자만 요약이 있어야 한다")
        assertEquals(1, summaries.single().activePetCount, "펫 한 마리면 ACTIVE 수는 1이어야 한다")
    }

    @Test
    fun `여러 사용자의 요약을 각자의 대표 펫과 수로 한 쿼리에 읽고 요청하지 않은 사용자는 뺀다`() {
        val first = createActiveUser()
        val second = createActiveUser()
        val notRequested = createActiveUser()
        tx.execute {
            val maltese = Breed.of(Species.DOG, "Maltese").also { em.persist(it) }
            val poodle = Breed.of(Species.DOG, "Poodle").also { em.persist(it) }
            val firstUser = em.find(User::class.java, first)
            em.persist(PetProfile.register(firstUser, poodle, "main", isDefault = true))
            em.persist(PetProfile.register(firstUser, maltese, "second"))
            em.persist(PetProfile.register(firstUser, maltese, "gone").also { it.delete() })
            val secondUser = em.find(User::class.java, second)
            em.persist(PetProfile.register(secondUser, maltese, "main", isDefault = true))
            em.persist(PetProfile.register(secondUser, poodle, "second"))
            em.persist(PetProfile.register(secondUser, poodle, "third"))
            em.persist(PetProfile.register(secondUser, poodle, "gone").also { it.delete() })
            em.persist(PetProfile.register(em.find(User::class.java, notRequested), poodle, "other", isDefault = true))
        }
        // 통계는 런타임에 켠다. 프로퍼티로 켜면 컨텍스트가 갈라진다
        val statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true
        statistics.clear()

        val summaries = petProfileUseCase.getDefaultPetSummaries(listOf(first, second)).associateBy { it.userId }
        val statements = statistics.prepareStatementCount

        assertEquals(setOf(first, second), summaries.keys, "요청한 두 사용자의 요약만 있어야 한다")
        assertEquals("Poodle", summaries.getValue(first).breedLabel, "첫 사용자는 자기 대표 펫의 품종이어야 한다")
        assertEquals(2, summaries.getValue(first).activePetCount, "첫 사용자는 삭제한 펫을 빼고 2마리여야 한다")
        assertEquals("Maltese", summaries.getValue(second).breedLabel, "둘째 사용자는 자기 대표 펫의 품종이어야 한다")
        assertEquals(3, summaries.getValue(second).activePetCount, "둘째 사용자는 삭제한 펫을 빼고 3마리여야 한다")
        assertEquals(1L, statements, "사용자 수와 관계없이 쿼리 한 번으로 읽어야 한다")
    }

    @Test
    fun `빈 사용자 목록이면 빈 결과다`() {
        assertTrue(petProfileUseCase.getDefaultPetSummaries(emptyList()).isEmpty(), "사용자가 없으면 요약도 없어야 한다")
    }
}
