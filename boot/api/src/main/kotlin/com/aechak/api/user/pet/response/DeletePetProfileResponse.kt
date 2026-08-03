package com.aechak.api.user.pet.response

import com.aechak.application.user.pet.usecase.result.DeletePetProfileResult

data class DeletePetProfileResponse(
    val promotedDefaultPetId: Long?,
) {
    companion object {
        fun from(result: DeletePetProfileResult): DeletePetProfileResponse = DeletePetProfileResponse(result.promotedDefaultPetId)
    }
}
