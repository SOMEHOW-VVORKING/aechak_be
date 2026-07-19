package com.aechak.api.user.pet.response

import com.aechak.application.user.pet.usecase.result.BreedResult

data class BreedResponse(
    val breedId: Long,
    val label: String,
) {
    companion object {
        fun from(result: BreedResult): BreedResponse = BreedResponse(result.breedId, result.label)
    }
}
