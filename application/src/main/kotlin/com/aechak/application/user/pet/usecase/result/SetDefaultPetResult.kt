package com.aechak.application.user.pet.usecase.result

import com.aechak.domain.user.pet.PetProfile

data class SetDefaultPetResult(
    val petId: Long,
    val isDefault: Boolean,
) {
    companion object {
        fun from(pet: PetProfile): SetDefaultPetResult = SetDefaultPetResult(pet.id, pet.isDefault)
    }
}
