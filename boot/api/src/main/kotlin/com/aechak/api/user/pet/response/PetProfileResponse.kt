package com.aechak.api.user.pet.response

import com.aechak.application.user.pet.usecase.result.PetProfileResult
import com.aechak.domain.user.pet.enums.PetStatus
import com.aechak.domain.user.pet.enums.Species
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class PetProfileResponse(
    val petId: Long,
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
    // 수정 API의 낙관적 락 토큰. 클라이언트가 이 값을 얻을 다른 경로가 없음
    val version: Int,
) {
    companion object {
        fun from(result: PetProfileResult): PetProfileResponse =
            PetProfileResponse(
                petId = result.petId,
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
                version = result.version,
            )
    }
}
