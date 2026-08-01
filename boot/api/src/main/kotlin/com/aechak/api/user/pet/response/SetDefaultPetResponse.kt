package com.aechak.api.user.pet.response

import com.aechak.application.user.pet.usecase.result.SetDefaultPetResult
import com.fasterxml.jackson.annotation.JsonProperty

data class SetDefaultPetResponse(
    val petId: Long,
    @get:JsonProperty("isDefault")
    val isDefault: Boolean,
) {
    companion object {
        fun from(result: SetDefaultPetResult): SetDefaultPetResponse = SetDefaultPetResponse(result.petId, result.isDefault)
    }
}
