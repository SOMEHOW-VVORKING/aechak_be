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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 계약 — 펫 등록·수정의 도메인 불변식. 깨지면 종이 안 맞는 품종이나 현실 밖 체중이 저장된다.
 */
class PetProfileTest {
    private val user = User.preRegister()
    private val dogBreed = Breed.of(Species.DOG, "말티즈")
    private val catBreed = Breed.of(Species.CAT, "코리안숏헤어")

    @Test
    fun `종은 품종에서 파생된다`() {
        // 컬럼으로 들고 있으면 breed와 어긋난 조합을 저장할 수 있어서 파생으로 둔다.
        val dog = PetProfile.register(user, dogBreed, "초코")
        val cat = PetProfile.register(user, catBreed, "나비")

        assertEquals(Species.DOG, dog.species)
        assertEquals(Species.CAT, cat.species)
    }

    @Test
    fun `체중이 0 이하면 거절한다`() {
        val ex =
            assertFailsWith<BusinessException> {
                PetProfile.register(user, dogBreed, "초코", weight = BigDecimal.ZERO)
            }

        assertEquals(UserErrorCode.INVALID_PET_WEIGHT, ex.errorCode)
    }

    @Test
    fun `체중이 200kg를 넘으면 거절한다`() {
        val ex =
            assertFailsWith<BusinessException> {
                PetProfile.register(user, dogBreed, "초코", weight = BigDecimal("200.1"))
            }

        assertEquals(UserErrorCode.INVALID_PET_WEIGHT, ex.errorCode)
    }

    @Test
    fun `경계값 0_1과 200_0은 허용한다`() {
        PetProfile.register(user, dogBreed, "초코", weight = BigDecimal("0.1"))
        PetProfile.register(user, dogBreed, "초코", weight = BigDecimal("200.0"))
    }

    @Test
    fun `체중을 안 주면 검증을 건너뛴다`() {
        val pet = PetProfile.register(user, dogBreed, "초코")

        assertEquals(null, pet.weight)
    }

    @Test
    fun `삭제하면 대표 플래그도 내려간다`() {
        val pet = PetProfile.register(user, dogBreed, "초코")
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
                name = "초코",
                profileImageKey = "pets/profile/abc.png",
            )

        assertEquals("pets/profile/abc.png", pet.profileImageKey)
    }

    @Test
    fun `validate는 register와 같은 규칙을 미리 돌려본다`() {
        val ex =
            assertFailsWith<BusinessException> {
                PetProfile.validate(dogBreed, BigDecimal("200.1"))
            }

        assertEquals(UserErrorCode.INVALID_PET_WEIGHT, ex.errorCode)
    }

    @Test
    fun `대표 지정이 상태에 반영된다`() {
        val pet = PetProfile.register(user, dogBreed, "초코")
        assertFalse(pet.isDefault, "기본값은 비대표여야 한다")

        pet.markAsDefault()

        assertTrue(pet.isDefault)
    }

    @Test
    fun `수정은 넘어온 값으로 통째로 덮는다`() {
        val pet = PetProfile.register(user, dogBreed, Species.DOG, "초코", "2022-04", BigDecimal("4.5"), "pets/profile/a.png")
        val other = Breed.of(Species.DOG, "푸들")

        pet.update(other, "초콜릿", null, null, null)

        assertEquals(other, pet.breed)
        assertEquals("초콜릿", pet.name)
        assertNull(pet.birthYearMonth, "안 넘긴 값은 지워져야 한다")
        assertNull(pet.weight, "안 넘긴 값은 지워져야 한다")
        assertNull(pet.profileImageKey, "안 넘긴 값은 지워져야 한다")
    }

    @Test
    fun `수정도 등록과 같은 불변식을 지킨다`() {
        val pet = PetProfile.register(user, dogBreed, Species.DOG, "초코")
        val catBreed = Breed.of(Species.CAT, "코리안숏헤어")

        assertFailsWith<BusinessException> { pet.update(catBreed, "초코", null, null, null) }
        assertFailsWith<BusinessException> { pet.update(dogBreed, "초코", null, BigDecimal("100.1"), null) }
        assertEquals(dogBreed, pet.breed, "거절된 수정은 상태를 남기지 않아야 한다")
    }

    @Test
    fun `종은 수정 대상이 아니다`() {
        val pet = PetProfile.register(user, dogBreed, Species.DOG, "초코")

        pet.update(dogBreed, "초콜릿", null, null, null)

        assertEquals(Species.DOG, pet.species, "update 시그니처에 species가 없다는 게 계약이다")
    }

    @Test
    fun `버전이 어긋나면 선점으로 판정한다`() {
        val pet = PetProfile.register(user, dogBreed, Species.DOG, "초코")

        pet.requireVersion(0)

        val ex = assertFailsWith<BusinessException> { pet.requireVersion(1) }
        assertEquals(UserErrorCode.PET_PROFILE_VERSION_CONFLICT, ex.errorCode)
    }

    @Test
    fun `기본 해제가 상태에 반영된다`() {
        val pet = PetProfile.register(user, dogBreed, Species.DOG, "초코", isDefault = true)

        pet.releaseDefault()

        assertFalse(pet.isDefault)
    }
}
