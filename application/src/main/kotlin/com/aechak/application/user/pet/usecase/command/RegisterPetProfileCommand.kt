package com.aechak.application.user.pet.usecase.command

import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.PetProfile
import com.aechak.domain.user.user.User
import java.math.BigDecimal

data class RegisterPetProfileCommand(
    val userId: Long,
    val name: String,
    val breedId: Long,
    val birthYearMonth: String?,
    val weight: BigDecimal?,
    val profileImageKey: String?,
    val isDefault: Boolean,
) {
    fun toEntity(
        user: User,
        breed: Breed,
        normalizedBirthYearMonth: String?,
        promotedImageKey: String?,
    ): PetProfile =
        PetProfile.register(
            user = user,
            breed = breed,
            name = name,
            birthYearMonth = normalizedBirthYearMonth,
            weight = weight,
            profileImageKey = promotedImageKey,
            isDefault = isDefault,
        )
}
