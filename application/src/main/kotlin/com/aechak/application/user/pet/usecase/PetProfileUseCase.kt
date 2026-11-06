package com.aechak.application.user.pet.usecase

import com.aechak.application.user.pet.usecase.command.RegisterPetProfileCommand
import com.aechak.application.user.pet.usecase.command.UpdatePetProfileCommand
import com.aechak.application.user.pet.usecase.result.DefaultPetSummaryResult
import com.aechak.application.user.pet.usecase.result.DeletePetProfileResult
import com.aechak.application.user.pet.usecase.result.PetProfileListResult
import com.aechak.application.user.pet.usecase.result.PetProfileResult
import com.aechak.application.user.pet.usecase.result.RegisterPetProfileResult
import com.aechak.application.user.pet.usecase.result.RepresentativePetResult
import com.aechak.application.user.pet.usecase.result.SetDefaultPetResult

interface PetProfileUseCase {
    fun registerPet(command: RegisterPetProfileCommand): RegisterPetProfileResult

    fun getPets(userId: Long): PetProfileListResult

    /** 여러 사용자의 대표 펫 요약. 대표 펫은 isDefault로 고르고 없으면 먼저 등록한 펫을 쓴다. ACTIVE 펫만 세고 펫이 없는 사용자는 빠진다 */
    fun getDefaultPetSummaries(userIds: Collection<Long>): List<DefaultPetSummaryResult>

    /** id 목록 중 ACTIVE 펫. 지운 펫은 빠지고 순서는 보장하지 않는다 */
    fun getActivePetsByIds(petIds: Collection<Long>): List<PetProfileResult>

    /** 대표 펫. 고르는 기준은 getDefaultPetSummaries와 같고 ACTIVE 펫이 없으면 null */
    fun findRepresentativePet(userId: Long): RepresentativePetResult?

    fun updatePet(command: UpdatePetProfileCommand): PetProfileResult

    fun deletePet(
        userId: Long,
        petId: Long,
    ): DeletePetProfileResult

    fun setDefaultPet(
        userId: Long,
        petId: Long,
    ): SetDefaultPetResult
}
