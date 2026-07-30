package com.aechak.api.user.pet.response

import com.aechak.application.user.pet.usecase.result.PetProfileListResult

data class PetProfileListResponse(
    val pets: List<PetProfileResponse>,
) {
    companion object {
        fun from(result: PetProfileListResult): PetProfileListResponse = PetProfileListResponse(result.pets.map(PetProfileResponse::from))
    }
}
