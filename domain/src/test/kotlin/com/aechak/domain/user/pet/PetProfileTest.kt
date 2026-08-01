package com.aechak.domain.user.pet

import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.pet.enums.Species
import com.aechak.domain.user.user.User
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 계약 — 펫 등록·수정의 도메인 불변식. 깨지면 종이 안 맞는 품종이나 현실 밖 체중이 저장된다.
 */
class PetProfileTest {
    private val user = User.preRegister()
    private val dogBreed = Breed.of(Species.DOG, "말티즈")
    private val catBreed = Breed.of(Species.CAT, "코리안숏헤어")

    @Test
    fun `품종의 종이 펫의 종과 다르면 거절한다`() {
        val ex =
            assertFailsWith<BusinessException> {
                PetProfile.register(user = user, breed = catBreed, species = Species.DOG, name = "초코")
            }

        assertEquals(UserErrorCode.INVALID_BREED, ex.errorCode)
    }

    @Test
    fun `체중이 0 이하면 거절한다`() {
        val ex =
            assertFailsWith<BusinessException> {
                PetProfile.register(user, dogBreed, Species.DOG, "초코", weight = BigDecimal.ZERO)
            }

        assertEquals(UserErrorCode.INVALID_PET_WEIGHT, ex.errorCode)
    }

    @Test
    fun `체중이 100kg를 넘으면 거절한다`() {
        val ex =
            assertFailsWith<BusinessException> {
                PetProfile.register(user, dogBreed, Species.DOG, "초코", weight = BigDecimal("100.1"))
            }

        assertEquals(UserErrorCode.INVALID_PET_WEIGHT, ex.errorCode)
    }

    @Test
    fun `경계값 0_1과 100_0은 허용한다`() {
        PetProfile.register(user, dogBreed, Species.DOG, "초코", weight = BigDecimal("0.1"))
        PetProfile.register(user, dogBreed, Species.DOG, "초코", weight = BigDecimal("100.0"))
    }

    @Test
    fun `체중을 안 주면 검증을 건너뛴다`() {
        val pet = PetProfile.register(user, dogBreed, Species.DOG, "초코")

        assertEquals(null, pet.weight)
    }

    @Test
    fun `삭제하면 대표 플래그도 내려간다`() {
        val pet = PetProfile.register(user, dogBreed, Species.DOG, "초코")
        pet.markAsDefault()

        pet.delete()

        assertFalse(pet.isDefault, "삭제된 펫은 대표가 아니어야 한다")
    }

    @Test
    fun `사진 키는 등록 시점에 함께 들어간다`() {
        val pet =
            PetProfile.register(
                user = user,
                breed = dogBreed,
                species = Species.DOG,
                name = "초코",
                profileImageKey = "pets/profile/abc.png",
            )

        assertEquals("pets/profile/abc.png", pet.profileImageKey)
    }

    @Test
    fun `validate는 register와 같은 규칙을 미리 돌려본다`() {
        val ex =
            assertFailsWith<BusinessException> {
                PetProfile.validate(catBreed, Species.DOG, null)
            }
        assertEquals(UserErrorCode.INVALID_BREED, ex.errorCode)

        assertFailsWith<BusinessException> {
            PetProfile.validate(dogBreed, Species.DOG, BigDecimal("100.1"))
        }
    }

    @Test
    fun `대표 지정이 상태에 반영된다`() {
        val pet = PetProfile.register(user, dogBreed, Species.DOG, "초코")
        assertFalse(pet.isDefault, "기본값은 비대표여야 한다")

        pet.markAsDefault()

        assertTrue(pet.isDefault)
    }
}
