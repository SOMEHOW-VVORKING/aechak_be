package com.aechak.application.user.pet.usecase.result

import com.aechak.domain.user.pet.PetProfile
import com.aechak.domain.user.pet.enums.PetStatus
import com.aechak.domain.user.pet.enums.Species
import java.math.BigDecimal

data class PetProfileResult(
    val petId: Long,
    val species: Species,
    val name: String,
    val breedId: Long,
    val breedLabel: String,
    val birthYearMonth: String?,
    val weight: BigDecimal?,
    // 수정은 전체 객체 전송이라 사진을 유지하려면 클라이언트가 key를 되돌려 줘야 함
    val profileImageKey: String?,
    val profileImageUrl: String?,
    val isDefault: Boolean,
    val status: PetStatus,
    val version: Int,
) {
    companion object {
        fun from(
            pet: PetProfile,
            profileImageUrl: String?,
        ): PetProfileResult =
            PetProfileResult(
                petId = pet.id,
                species = pet.species,
                name = pet.name,
                breedId = pet.breed.id,
                breedLabel = pet.breed.label,
                birthYearMonth = pet.birthYearMonth,
                weight = pet.weight,
                profileImageKey = pet.profileImageKey,
                profileImageUrl = profileImageUrl,
                isDefault = pet.isDefault,
                status = pet.status,
                version = pet.version,
            )
    }
}
