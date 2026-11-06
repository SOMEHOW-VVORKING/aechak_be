package com.aechak.api.user.pet

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.PetProfile
import com.aechak.domain.user.pet.enums.Species
import com.aechak.domain.user.user.User
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 통합: 피드 카드가 페이지의 펫 태그를 펫 id 목록으로 한 번에 읽을 때 ACTIVE 펫만 품종까지 싣고 쿼리 수가 펫 수와 상관없는지.
 * 깨지면 카드마다 펫과 품종을 따로 읽어 쿼리가 늘거나 지운 펫이 카드에 다시 나온다.
 */
class ActivePetsByIdsIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var petProfileUseCase: PetProfileUseCase

    @Test
    fun `여러 사용자의 펫을 품종 이름까지 한 쿼리로 읽는다`() {
        val owners = List(3) { createActiveUser() }
        val (first, second, third) =
            tx.execute {
                val maltese = Breed.of(Species.DOG, "Maltese").also { em.persist(it) }
                val poodle = Breed.of(Species.DOG, "Poodle").also { em.persist(it) }
                val korean = Breed.of(Species.CAT, "Korean").also { em.persist(it) }
                listOf(
                    pet(owners[0], maltese, "2021-03"),
                    pet(owners[1], poodle, null),
                    pet(owners[2], korean, "2019-12"),
                )
            }!!
        val statistics = statistics()

        val pets = petProfileUseCase.getActivePetsByIds(listOf(first, second, third)).associateBy { it.petId }

        assertEquals(1L, statistics.prepareStatementCount, "펫 수와 관계없이 쿼리 한 번으로 읽어야 한다")
        assertEquals(setOf(first, second, third), pets.keys, "요청한 펫이 모두 있어야 한다")
        assertEquals("Maltese", pets.getValue(first).breedLabel, "품종 이름이 채워져야 한다")
        assertEquals("2021-03", pets.getValue(first).birthYearMonth, "생년월이 채워져야 한다")
        assertEquals("Poodle", pets.getValue(second).breedLabel, "다른 사용자의 펫도 품종 이름이 채워져야 한다")
        assertEquals("Korean", pets.getValue(third).breedLabel, "다른 종의 펫도 품종 이름이 채워져야 한다")
    }

    @Test
    fun `지운 펫은 빠진다`() {
        val owner = createActiveUser()
        val (kept, deleted) =
            tx.execute {
                val breed = Breed.of(Species.DOG, "Maltese").also { em.persist(it) }
                listOf(pet(owner, breed, null), pet(owner, breed, null, deleted = true))
            }!!

        val pets = petProfileUseCase.getActivePetsByIds(listOf(kept, deleted))

        assertEquals(listOf(kept), pets.map { it.petId }, "지운 펫은 결과에 없어야 한다")
    }

    @Test
    fun `빈 목록이면 쿼리를 보내지 않는다`() {
        val statistics = statistics()

        val pets = petProfileUseCase.getActivePetsByIds(emptyList())

        assertTrue(pets.isEmpty(), "펫 id가 없으면 결과도 없어야 한다")
        assertEquals(0L, statistics.prepareStatementCount, "펫 id가 없으면 쿼리를 보내지 않아야 한다")
    }

    private fun pet(
        ownerId: Long,
        breed: Breed,
        birthYearMonth: String?,
        deleted: Boolean = false,
    ): Long {
        val pet = PetProfile.register(em.find(User::class.java, ownerId), breed, "코코", birthYearMonth = birthYearMonth)
        if (deleted) pet.delete()
        em.persist(pet)
        em.flush()
        return pet.id
    }

    // 통계는 런타임에 켠다. 프로퍼티로 켜면 컨텍스트가 갈라진다
    private fun statistics(): Statistics =
        em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics.apply {
            isStatisticsEnabled = true
            clear()
        }
}
