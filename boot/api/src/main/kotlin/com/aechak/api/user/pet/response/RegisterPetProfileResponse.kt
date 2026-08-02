package com.aechak.api.user.pet.response

import com.aechak.application.user.pet.usecase.result.RegisterPetProfileResult
import com.aechak.domain.user.pet.enums.PetStatus
import com.aechak.domain.user.pet.enums.Species
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class RegisterPetProfileResponse(
    val petId: Long,
    val userId: Long,
    val species: Species,
    val name: String,
    val breedId: Long,
    val breedLabel: String,
    val birthYearMonth: String?,
    val weight: BigDecimal?,
    val profileImageKey: String?,
    val profileImageUrl: String?,
    @get:JsonProperty("isDefault")
    val isDefault: Boolean,
    val status: PetStatus,
) {
    companion object {
        fun from(result: RegisterPetProfileResult): RegisterPetProfileResponse =
            RegisterPetProfileResponse(
                petId = result.petId,
                userId = result.userId,
                species = result.species,
                name = result.name,
                breedId = result.breedId,
                breedLabel = result.breedLabel,
                birthYearMonth = result.birthYearMonth,
                weight = result.weight,
                profileImageKey = result.profileImageKey,
                profileImageUrl = result.profileImageUrl,
                isDefault = result.isDefault,
                status = result.status,
            )
    }
}
